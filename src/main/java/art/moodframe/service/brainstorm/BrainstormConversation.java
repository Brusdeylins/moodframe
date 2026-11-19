package art.moodframe.service.brainstorm;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.ResearchNote;
import art.moodframe.model.SavedImage;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.util.I18nManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * One answer of the brainstorm assistant to a message: the system prompt, the videos the message links, the tool
 * loop with the language model of the session, the parsed reply with its facts checked and its building blocks
 * referred to the lists. It stores nothing; {@link BrainstormService} queues the requests and stores the answer.
 *
 * @since 2.1.3
 */
final class BrainstormConversation {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormConversation.class);
    private static final String TEMPLATE_RESOURCE = "/brainstorm/system-prompt.txt";
    static final int MAX_TOOL_CALLS = 3;
    /** The most requests of one answer, the lookups of the library included; the last one has to answer. */
    static final int MAX_ROUNDS = 6;

    /**
     * An answer of the assistant.
     *
     * @param reply the parsed reply, with the research of this turn
     * @param raw   the text of the language model, as it is stored
     */
    record Answer(BrainstormReply reply, String raw) {
    }

    private final BrainstormModels models;
    private final BrainstormTools tools;
    private final Executor executor;
    private final String template = loadTemplate();

    /**
     * Creates the conversation.
     *
     * @param models   the language models
     * @param tools    the tools of the assistant
     * @param executor the threads the tool calls of an answer run on, in parallel
     */
    BrainstormConversation(BrainstormModels models, BrainstormTools tools, Executor executor) {
        this.models = models;
        this.tools = tools;
        this.executor = executor;
    }

    private static String loadTemplate() {
        try (InputStream in = BrainstormConversation.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null)
                throw new IllegalStateException("Missing resource " + TEMPLATE_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot read " + TEMPLATE_RESOURCE, e);
        }
    }

    /**
     * Asks the language model of the session for the answer to the last message of the history.
     *
     * @param session      the session
     * @param message      the message to answer, the last one of the history
     * @param history      the stored messages of the session
     * @param currentDraft the latest draft as JSON text, empty if there is none
     * @param context      the selectable values and the current main window state
     * @param status       told what the assistant is about to research
     * @param check        throws if the session was deleted meanwhile, so nothing else is asked
     * @return the answer
     * @throws IllegalStateException with a user-friendly message if the answer is unusable
     */
    Answer answer(BrainstormSession session, BrainstormMessage message, List<BrainstormMessage> history,
            String currentDraft, BrainstormContext context, Consumer<String> status, Runnable check) {
        String systemPrompt = BrainstormPromptBuilder.build(template, BrainstormService.language(), context,
                currentDraft);
        List<ResearchNote> notes = new ArrayList<>();
        List<String> videoTurns = tools.watchLinked(new BrainstormTools.Request(session.getId(), models.of(session),
                status, check), message, notes);
        String raw = askWithTools(session, systemPrompt, history, videoTurns, notes, status, check);

        BrainstormReply reply;
        try {
            reply = BrainstormReply.parse(raw);
        }
        catch (IllegalArgumentException e) {
            logger.error("Unusable brainstorm reply: {}", raw, e);
            throw new IllegalStateException(I18nManager.getText("brainstorm.error.invalid.reply"), e);
        }
        BrainstormFactCheck.markUnverifiedFacts(reply.getDraft(), history, notes);
        resolveBlocks(reply.getDraft(), context);
        reply.setResearch(notes);
        return new Answer(reply, raw);
    }

    /**
     * Asks the model and runs its tool calls until it answers with the JSON reply. After {@value #MAX_TOOL_CALLS}
     * calls of the research and the videos, or in the last of {@value #MAX_ROUNDS} requests, the model has to answer;
     * the lookups of the library are no research and do not count. The turns of a conversation are rebuilt from the
     * stored text messages, the watched videos and the tool rounds of the current turn exist only here. The language
     * model is read for every request, so a change of the engine applies at once.
     */
    private String askWithTools(BrainstormSession session, String systemPrompt, List<BrainstormMessage> history,
            List<String> videoTurns, List<ResearchNote> notes, Consumer<String> status, Runnable check) {
        List<ChatTurn> turns = new ArrayList<>();
        for (BrainstormMessage message : history)
            turns.add(BrainstormMessage.ROLE_ASSISTANT.equals(message.getRole())
                    ? new ChatTurn.Model(message.getContent()) : new ChatTurn.User(message.getContent()));
        for (String turn : videoTurns)
            turns.add(new ChatTurn.User(turn));

        int calls = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            check.run();
            ChatModel model = models.of(session);
            ChatTurn.Model answer = model.chatTurn(systemPrompt, List.copyOf(turns), tools.offeredTo(model),
                    calls >= MAX_TOOL_CALLS || round == MAX_ROUNDS - 1).join();
            if (answer.calls().isEmpty())
                return answer.text();

            turns.add(answer);

            /*  The calls of one answer are independent, so they run in parallel  */
            BrainstormTools.Request request = new BrainstormTools.Request(session.getId(), model, status, check);
            List<CompletableFuture<BrainstormTools.Run>> runs = new ArrayList<>();
            for (ChatTurn.ToolCall call : answer.calls()) {
                if (!tools.looksUp(call))
                    calls++;
                runs.add(CompletableFuture.supplyAsync(() -> tools.run(request, call), executor));
            }
            List<ChatTurn.ToolResult> results = new ArrayList<>();
            for (CompletableFuture<BrainstormTools.Run> run : runs) {
                BrainstormTools.Run result = run.join();
                check.run();
                results.add(result.result());
                if (result.note() != null)
                    notes.add(result.note());
            }
            turns.add(new ChatTurn.ToolResults(results));
        }
        throw new IllegalStateException(I18nManager.getText("brainstorm.error.no.response"));
    }

    /**
     * Refers the mood, the building blocks and the frame of the draft that name an entry of the lists to that entry by
     * id, so a stored draft still finds an entry the user renamed later; mood, frame and saved images by their id,
     * else by name. A name without entry keeps the reference as it is, a saved image without entry is dropped.
     */
    private static void resolveBlocks(BrainstormDraft draft, BrainstormContext context) {
        for (Mood.Field field : BrainstormDraft.FIELDS) {
            BrainstormDraft.Block block = draft.getBlock(field);
            String id = block != null && !block.isNew() ? context.idOf(field, block.name()) : null;
            if (id != null)
                draft.setBlock(field, block.withId(id));
        }
        Mood mood = BrainstormDraft.Block.find(draft.getMood(), context.moods(), Mood::getId, Mood::getName);
        if (mood != null)
            draft.setMood(new BrainstormDraft.Block(mood.getId(), mood.getName(), null, List.of(), false));
        Frame frame = BrainstormDraft.Block.find(draft.getFrame(), context.frames(), Frame::getId, Frame::getName);
        if (frame != null)
            draft.setFrame(new BrainstormDraft.Block(frame.getId(), frame.getName(), null, List.of(), false));
        List<BrainstormDraft.Block> images = new ArrayList<>();
        for (BrainstormDraft.Block reference : draft.getSavedImages()) {
            SavedImage image = BrainstormDraft.Block.find(reference, context.savedImages(), SavedImage::id,
                    SavedImage::name);
            if (image != null && images.stream()
                    .noneMatch((BrainstormDraft.Block added) -> added.id().equals(image.id())))
                images.add(new BrainstormDraft.Block(image.id(), image.name(), null, List.of(), false));
        }
        draft.getSavedImages().clear();
        draft.getSavedImages().addAll(images);
    }
}
