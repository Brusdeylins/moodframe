package art.moodframe.service.brainstorm;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.ResearchNote;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.service.provider.openai.OpenAiApiService;
import art.moodframe.util.I18nManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs the guided brainstorm chat of a window: queues the requests of each session, lets
 * {@link BrainstormConversation} answer them and stores messages and draft versions in {@link BrainstormSessions}.
 * Every session is independent: requests of different sessions run in parallel, requests of one session run one
 * after the other, and deleting a session stops its running request. The queue belongs to the shared sessions, so
 * this holds across windows: a request still running after its window closed holds back the next one of the session
 * in another window, and deleting the session stops it. With Gemini, the YouTube videos a message links
 * are watched before the answer, with the message as instruction ({@link BrainstormTools}).
 *
 * <p>Threads: {@link #sendUserMessage}, {@link #sendEvent} and {@link #noteAdopted} only queue the request and
 * return at once, so the JavaFX thread may call them; the request reads and writes the session on a thread of the
 * queue of its session, after the request before it. Every other method that reads or writes the sessions blocks on
 * the database and is called off the JavaFX thread (the window runs it on its worker). The listeners are called on
 * background threads.</p>
 *
 * @since 1.7.0
 */
public class BrainstormService {

    private static final Logger logger = LoggerFactory.getLogger(BrainstormService.class);
    private static final int TITLE_MAX_LENGTH = 40;
    /** From this step on the topic is clear, so the session gets its short name. */
    private static final int FIRST_STEP_WITH_TOPIC = 2;
    private static final int SHORT_TITLE_MAX_LENGTH = 24;
    private static final int STORED_RESULT_MAX_LENGTH = 1500;

    /*  The threads of the turns, their tool calls and the naming of a session: they wait for the language model and
        the research, which run on the IoExecutor, so they run on virtual threads of their own and never hold a worker
        of the IoExecutor while they wait (a bounded pool whose tasks wait for each other can stall)  */
    static final Executor TURNS = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("brainstorm-", 1).factory());

    private final BrainstormSessions sessions;
    private final BrainstormModels models;
    private final BrainstormTools tools;
    private final BrainstormConversation conversation;
    private BiConsumer<String, String> onToolStatus = (String sessionId, String status) -> { };
    private Consumer<BrainstormSession> onTitleChanged = (BrainstormSession session) -> { };
    /** The queue of the sessions, shared with every other window on the same sessions. */
    private final BrainstormQueue queue;

    /** Creates a service for each brainstorm window, with the sessions and language models of the application. */
    @FunctionalInterface
    public interface Factory {

        /**
         * Creates a service.
         *
         * @return the service
         */
        BrainstormService create();
    }

    /**
     * Creates the service.
     *
     * @param sessions the stored sessions
     * @param models   the language models, chosen for every request
     * @param tools    the tools of the assistant
     */
    public BrainstormService(BrainstormSessions sessions, BrainstormModels models, BrainstormTools tools) {
        this.sessions = sessions;
        this.queue = sessions.queue();
        this.models = models;
        this.tools = tools;
        this.conversation = new BrainstormConversation(models, tools, TURNS);
    }

    /**
     * Creates the service with Gemini only and without the lookup of YouTube videos.
     *
     * @param repository the session storage
     * @param geminiService the Gemini API service
     */
    public BrainstormService(BrainstormRepository repository, GeminiApiService geminiService) {
        this(repository, geminiService, null, null);
    }

    /**
     * Creates the service that can also talk to OpenAI, without the lookup of YouTube videos. The engine is read from
     * the configuration for every request, so a change in the dialog applies to the next message.
     *
     * @param repository the session storage
     * @param geminiService the Gemini API service, also does the research
     * @param openAiService the OpenAI API service, null if there is none
     * @param config the configuration with the chosen engine and OpenAI language model, null for Gemini only
     */
    public BrainstormService(BrainstormRepository repository, GeminiApiService geminiService,
            OpenAiApiService openAiService, ApplicationConfig config) {
        this(new BrainstormSessions(repository), new BrainstormModels(geminiService, openAiService, config),
                new BrainstormTools(geminiService, null));
    }

    /**
     * Sets the listener that is told what the AI is about to research (a search question or a video URL),
     * called from a background thread before the tool runs, with the id of the session and the status.
     *
     * @param onToolStatus the listener
     */
    public void setOnToolStatus(BiConsumer<String, String> onToolStatus) {
        this.onToolStatus = onToolStatus;
    }

    /**
     * Sets the listener that is told when a session got its short name, called from a background thread.
     *
     * @param onTitleChanged the listener
     */
    public void setOnTitleChanged(Consumer<BrainstormSession> onTitleChanged) {
        this.onTitleChanged = onTitleChanged;
    }

    /** Creates a new empty session. */
    public Optional<BrainstormSession> startSession() {
        return startSession(null);
    }

    /**
     * Creates a new empty session that is answered by the given language model.
     *
     * @param engine "gemini" or "openai", null to follow the choice of the window
     * @return the session, empty if it could not be stored
     */
    public Optional<BrainstormSession> startSession(String engine) {
        return sessions.create(I18nManager.getText("brainstorm.session.new"), engine);
    }

    /**
     * Sets the language model that answers a session from its next message on.
     *
     * @param session the session
     * @param engine "gemini" or "openai"
     */
    public void setEngine(BrainstormSession session, String engine) {
        session.setEngine(engine);
        sessions.updateEngine(session.getId(), engine);
    }

    public List<BrainstormSession> listSessions() {
        return sessions.list();
    }

    /**
     * Moves a session to another place in the list and stores the order of all sessions.
     *
     * @param list     all sessions in their order
     * @param session  the session to move
     * @param before   the session it goes in front of, null for the end of the list
     * @return the sessions in their new order
     * @since 2.0.0
     */
    public List<BrainstormSession> moveSession(List<BrainstormSession> list, BrainstormSession session,
            BrainstormSession before) {
        List<BrainstormSession> order = new ArrayList<>(list);
        if (session == before) {
            return order;
        }
        order.remove(session);
        order.add(before != null ? order.indexOf(before) : order.size(), session);
        sessions.storeOrder(order);
        return order;
    }

    /**
     * Deletes a session. A request that is still running for it stops before its next AI or research call and
     * stores nothing any more.
     *
     * @param sessionId the session id
     * @return true if the session was deleted
     */
    public boolean deleteSession(String sessionId) {
        queue.delete(sessionId);
        return sessions.delete(sessionId);
    }

    /**
     * The requests of a session queued so far, also those a closed window sent, so a window opened later can wait
     * for their answer.
     *
     * @param sessionId the session id
     * @return completes when they are done, whether they failed or not; at once if there are none
     */
    public CompletableFuture<Void> pending(String sessionId) {
        return queue.pending(sessionId);
    }

    /** Throws if the session was deleted, so a running request ends without any further call or write. */
    private void ensureNotDeleted(String sessionId) {
        if (queue.isDeleted(sessionId)) {
            throw new CancellationException("Brainstorm session " + sessionId + " was deleted");
        }
    }

    /**
     * Renames a session on the queue of the session, after the requests before it, so the short name a running
     * request makes never overwrites the new title; returns at once.
     *
     * @param sessionId the session id
     * @param title the new, non-empty title
     * @return completes when the title is stored
     */
    public CompletableFuture<Void> renameSession(String sessionId, String title) {
        return enqueue(sessionId, () -> {
            sessions.updateTitle(sessionId, title);
            return null;
        });
    }

    public List<BrainstormMessage> loadMessages(String sessionId) {
        return sessions.messages(sessionId);
    }

    public Optional<BrainstormDraft> latestDraft(String sessionId) {
        return sessions.latestDraft(sessionId);
    }

    /**
     * Looks up the title and the length of a YouTube video, for its card in the chat.
     *
     * @param videoId the id of the video
     * @return the title and the length, empty values if unknown; never fails
     */
    public CompletableFuture<YouTubeInfoService.Info> videoInfo(String videoId) {
        return tools.videoInfo(videoId);
    }

    /**
     * Loads a preview image of a YouTube video.
     *
     * @param url the URL of the image
     * @return the bytes of the image; fails if it cannot be loaded
     */
    public CompletableFuture<byte[]> videoImage(String url) {
        return tools.videoImage(url);
    }

    /**
     * Sends a user message and returns the AI reply. The first message of a session is its title until the session
     * gets its short name.
     *
     * @param session the session
     * @param text the user message
     * @param context the selectable values and the current main window state
     * @return the parsed reply; fails with a user-friendly message
     */
    public CompletableFuture<BrainstormReply> sendUserMessage(
            BrainstormSession session, String text, BrainstormContext context) {
        return send(session, new BrainstormMessage(BrainstormMessage.ROLE_USER, text), context);
    }

    /**
     * Tells the AI about a change in the panel (for example a new target model).
     *
     * @param session the session
     * @param eventJson the event as JSON text, for example {@code {"event":"target_model_changed",...}}
     * @param context the selectable values and the current main window state
     * @return the parsed reply
     */
    public CompletableFuture<BrainstormReply> sendEvent(
            BrainstormSession session, String eventJson, BrainstormContext context) {
        return send(session, new BrainstormMessage(BrainstormMessage.ROLE_EVENT,
                BrainstormSessions.EVENT_PREFIX + eventJson), context);
    }

    /**
     * The app event of a changed time range of a linked video: Gemini watches the new clip with the message that
     * linked the video as instruction before the AI answers.
     *
     * @param clip the video with the new range
     * @param instruction the message that linked the video
     * @return the event, to be sent with {@link #sendEvent}
     */
    public static JSONObject videoRangeEvent(YouTubeClip clip, String instruction) {
        return new JSONObject().put("event", BrainstormTools.VIDEO_RANGE_EVENT).put("url", clip.watchUrlAt(0))
                .put("start_seconds", clip.startSeconds()).put("end_seconds", clip.endSeconds())
                .put("instruction", instruction);
    }

    /**
     * Notes in the session that the user adopted the draft, without asking the AI: it reads the event with the next
     * message, so another image asked for afterwards starts a new round.
     *
     * @param session the session
     * @return completes when the event is stored, after the requests of the session before it
     */
    public CompletableFuture<Void> noteAdopted(BrainstormSession session) {
        return enqueue(session.getId(), () -> {
            sessions.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_EVENT,
                    BrainstormSessions.EVENT_PREFIX + new JSONObject().put("event", "draft_adopted")));
            return null;
        });
    }

    private CompletableFuture<BrainstormReply> send(
            BrainstormSession session, BrainstormMessage message, BrainstormContext context) {

        /*  The history is read when the request is its turn, so it contains the answer of the one before  */
        return enqueue(session.getId(), () -> process(session, message, context));
    }

    /** Runs work of a session after the work of the session queued before it, on a thread of its own. */
    private <T> CompletableFuture<T> enqueue(String sessionId, Supplier<T> work) {
        return queue.enqueue(sessionId, work, TURNS);
    }

    private BrainstormReply process(BrainstormSession session, BrainstormMessage message, BrainstormContext context) {
        String sessionId = session.getId();
        ensureNotDeleted(sessionId);
        sessions.addMessage(sessionId, message);
        List<BrainstormMessage> history = sessions.messages(sessionId);
        if (BrainstormMessage.ROLE_USER.equals(message.getRole()) && history.size() == 1) {
            session.setTitle(firstMessageTitle(message.getContent()));
            sessions.updateTitle(sessionId, session.getTitle());
        }
        String currentDraft = sessions.latestDraft(sessionId)
                .map((BrainstormDraft draft) -> BrainstormJson.toJson(draft).toString()).orElse("");
        BrainstormConversation.Answer answer = conversation.answer(session, message, history, currentDraft, context,
                (String status) -> onToolStatus.accept(sessionId, status), () -> ensureNotDeleted(sessionId));
        BrainstormReply reply = answer.reply();

        // The session may have been deleted while the AI was answering: then nothing is stored any more
        ensureNotDeleted(sessionId);
        for (ResearchNote note : reply.getResearch()) {
            sessions.addMessage(sessionId, new BrainstormMessage(BrainstormMessage.ROLE_EVENT,
                    BrainstormSessions.EVENT_PREFIX + BrainstormJson.researchEvent(note, STORED_RESULT_MAX_LENGTH)));
        }
        sessions.addMessage(sessionId, new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, answer.raw()));
        sessions.addDraft(sessionId, reply.getDraft());
        nameSessionOnce(session, history, reply);
        return reply;
    }

    private static String firstMessageTitle(String text) {
        return text.length() > TITLE_MAX_LENGTH ? text.substring(0, TITLE_MAX_LENGTH).trim() + "…" : text;
    }

    /**
     * Gives the session a short name (one or two words) once the topic is clear, instead of the first sentence of
     * the user. A title the user changed is kept: only a title that is still the first sentence is replaced, checked
     * again when the name is stored on the queue of the session, so a rename during the naming wins. The name is
     * made in the background, a failure keeps the old title.
     */
    private void nameSessionOnce(BrainstormSession session, List<BrainstormMessage> history, BrainstormReply reply) {
        String firstUserMessage = history.stream()
                .filter((BrainstormMessage message) -> BrainstormMessage.ROLE_USER.equals(message.getRole()))
                .map(BrainstormMessage::getContent).findFirst().orElse("");
        String automatic = firstMessageTitle(firstUserMessage);
        if (reply.getStep() < FIRST_STEP_WITH_TOPIC || !session.getTitle().equals(automatic)) {
            return;
        }

        String content = "First message: " + firstUserMessage + "\nCurrent prompt: " + reply.getDraft().getPrompt();
        CompletableFuture.runAsync(() -> {
            try {
                String title = models.of(session).suggestTitle(content, language()).join()
                        .replaceAll("[\"'„“”.\\n]", "").trim();
                if (title.isEmpty() || queue.isDeleted(session.getId())) {
                    return;
                }
                String name = title.length() > SHORT_TITLE_MAX_LENGTH
                        ? title.substring(0, SHORT_TITLE_MAX_LENGTH).trim() : title;
                enqueue(session.getId(), () -> applyName(session, automatic, name));
            } catch (RuntimeException e) {
                logger.warn("Could not name the brainstorm session: {}", e.getMessage());
            }
        }, TURNS);
    }

    /*  On the queue of the session: the short name replaces the title only while it is still the first sentence  */
    private Void applyName(BrainstormSession session, String automatic, String name) {
        if (queue.isDeleted(session.getId()) || !sessions.replaceTitle(session.getId(), automatic, name))
            return null;
        session.setTitle(name);
        onTitleChanged.accept(session);
        return null;
    }

    /** The name of the language of the user interface, for example "German". */
    static String language() {
        return "de".equals(I18nManager.getCurrentLocale().getLanguage()) ? "German" : "English";
    }
}
