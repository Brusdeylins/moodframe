# MoodFrame Architecture

MoodFrame is a JavaFX 23 desktop application (Java 21, one JPMS module `art.moodframe`). It generates images and
videos with FLUX (BFL), Gemini and OpenAI and keeps chats, moods, frames and building blocks in a local SQLite
library under `~/.moodframe`. This document describes the layers, the threading model, how services find each other,
the persistence and the main decisions. The `package-info.java` of each package lists its main classes.

## Layers

```
 ui (controller, brainstorm, panel, dialog,        mcp
     cell, entry, component, dnd, support)
            \                                      /
             +--------------> service <-----------+
                                 |          \
                             repository     net
                                 |
                              database
 ---------------------------------------------------------
 model, util, config: used by every layer above
```

| Package      | Role                                                                                   |
|--------------|----------------------------------------------------------------------------------------|
| `ui`         | JavaFX views: FXML controllers, dialogs, list cells, components, drag and drop         |
|              | (`ui.brainstorm` holds the brainstorm window as one feature package; `ui.entry` the    |
|              | chat entries with their images: cards, images view, full view; `ui.panel` the groups   |
|              | Mood and Frame, which other windows can show; `ui.component` the generic controls;     |
|              | `ui.support` the helpers that need JavaFX or the desktop: themes, videos, opening      |
|              | files and web pages, listener cleanup, `FxI18n`, `CoalescedLoad`, the decoded          |
|              | thumbnails in `ImageCache`, `ListReorder`, `BoxInset`, `StatusSink`, and the texts     |
|              | several views share without JavaFX (`SafetyTolerance`), as it is the lowest part of    |
|              | `ui` they all may use; the root holds only `SplashScreen`, shown before the window)    |
| `mcp`        | MCP server for local AI agents, a second adapter beside the UI on the same services    |
| `service`    | application logic, split by topic into subpackages (see below)                         |
| `net`        | network access: HTTP clients with proxy and corporate certificates, the certificate     |
|              | folder (`CertificateStore`), the pool of the blocking network work (`IoExecutor`)       |
| `repository` | data access with plain JDBC, one repository per aggregate                              |
| `database`   | SQLite connections, WAL mode, schema migrations                                         |
| `model`      | domain data classes, records and enums                                                  |
| `util`       | general helpers without JavaFX: i18n, images, secrets, the version, the downloads      |
| `config`     | folders of the library (`LibraryPaths`), settings (`ApplicationConfig` and its          |
|              | sections), key encryption, the service locator (`ServiceFactory`)                       |

Allowed dependency directions:

- `ui` → `service` → `repository` → `database`; each layer may also use `model`, `util` and `config`.
- `ui` → `net` and `service` → `net`: the provider clients and the downloads build their HTTP clients with
  `net.HttpClients` and run on `net.IoExecutor`; the settings dialog manages the certificates with
  `net.CertificateStore` and tells `HttpClients` that the proxy changed, which is all the user interface uses of
  `net` (its own disk work, e.g. a pasted image, runs on its own workers, not on the network pool). `net` uses only
  `config` and `util`.
- `mcp` → `service`, never `ui` or `net`. Whatever the UI can do to the library, the MCP tools do through the same
  services. `service` reaches `database` only through `repository`.
- `ui` → `mcp` only for the lifecycle of the server: the settings dialog starts, stops and restarts
  `mcp.McpService` and shows its command line. No view uses the tools of the server.
- `model` depends on nothing of the application. A value type stored as JSON carries that form itself (`EntryMood`,
  `EntryFrame`, `CameraSettings`, read by several layers), `ColorStyle` the Jackson names of the palette file; what
  a language model exchanges (the brainstorm draft, the research events) is `service.brainstorm.BrainstormJson`, as
  `model/package-info.java` states and `ArchitectureTest` checks. `GenerationModel` names only the i18n key of a localized display
  name (`displayNameKey`); `service.generation.ModelLabels` gives the dropdown items in the current language. An item
  in any language leads back to its model with `GenerationModel.fromLocalizedDisplayName`, since a localized name
  differs from the display name only after the family name.
- `util` may use `config` and uses nothing today; `config` uses only `model` (not `util`, so the two never form a
  cycle); neither `util` nor `model` imports JavaFX, except `ChatEntry` and `GeneratedImage` with the properties the
  views observe (see the known deviations below). `I18nManager` notifies each listener through the executor it was
  registered with; the views register with `ui.support.FxI18n`, which notifies them on the JavaFX thread.
- No layer depends on a layer above it. Where a lower layer needs something from above, it owns an interface and
  the upper layer implements it, e.g. `repository.ChatFiles` implemented by `service.chat.ChatFileService`.
- `ArchitectureTest` checks these directions (a new top-level package fails until it gets its rules), the order of
  the service subpackages below, that no packages depend on each other in a cycle, that `model` (besides the known
  deviation), `util` and `service.chat` need no JavaFX, that `service.generation.FxModel` is the only class of
  `service` that uses JavaFX, that no class outside `ui` and the root uses the `ServiceFactory` (besides itself) and
  in `ui` only `MainWindowController` and `SettingsController` do, the order of the parts of `ui` (below), that `ui` uses of `mcp` only `McpService` and of `net` only
  `HttpClients` and `CertificateStore`, that no class imports a package of the application with a wildcard and
  that the clients of the providers (`service.provider.bfl`, `.gemini`, `.openai`) use no class of each other (D4,
  D6), from the imports and fully qualified names of `src/main/java`.
- The parts of `ui` use only those below them: `ui.support` at the bottom, then `ui.dnd`, `ui.component`,
  `ui.entry`, `ui.cell`, `ui.dialog`, `ui.panel`, `ui.brainstorm`, the root (`SplashScreen`),
  `ui.controller.settings` and `ui.controller` on top, so no view uses a controller and the entry cards build on the
  generic controls; the order allows the brainstorm dialog to show the groups of `ui.panel`, which it does not do yet
  (planned with ticket 136).

The subpackages of `service`, each using only those above it in the table (no cycles):

| Package                       | Role                                                                                 |
|-------------------------------|--------------------------------------------------------------------------------------|
| `service.provider`            | the provider SPI and registry, the types the providers share (errors, jobs, chat     |
|                               | turns, request options)                                                              |
| `service.provider.bfl`        | the client of BFL (FLUX), whose images are jobs to poll                              |
| `service.provider.gemini`     | the client of Gemini: images, Omni videos, the brainstorm chat                       |
| `service.provider.openai`     | the client of OpenAI or an OpenAI-compatible gateway                                 |
| `service.provider.validation` | the check of the accounts of all providers (`ApiProviderValidator`)                  |
| `service.prompt`              | the prompt text: `PromptComposer`, `CameraPromptBuilder`                             |
| `service.trash`               | the central trash (`TrashCenter`, `TrashGroup`), its cleanup, the trash directory    |
| `service.library`             | moods, frames, building blocks, palettes, favorites, `LibraryEvents`, trash groups   |
| `service.chat`                | the chats with their entries, files, thumbnail files, attachments, their trash       |
|                               | groups; no JavaFX                                                                    |
| `service.generation`          | carries out a generation order: entry, request, job polling, image files             |
| `service.brainstorm`          | the brainstorm chat and the analysis of an image into a mood                         |
| `service`                     | `ConfigurationExporter` (settings and library backup), `UpdateCheckService`          |

The clients of the providers use of the services only `service.provider`, and none of them uses another, so a
client can change or go without touching the others (D4).

`service.trash` owns the interface `TrashGroup`; the groups of chats, images, moods, frames and blocks live with their
items in `service.chat` and `service.library`, so the trash depends on none of them.

Known deviations: two model classes carry what the views observe while a generation runs, as JavaFX properties and
lists. `ChatEntry` has its status and prompt as properties and its images as an `ObservableList`: the entry card
(`ui.entry.ChatEntryCard`) follows all three, the images view (`ui.entry.ImageGrid`) the list of images.
`GeneratedImage` has its download status as a property, which both follow, and its four sizes (`width`, `height`,
`imageWidth`, `imageHeight`), from which the entry card shapes the tile. Every other value of the two, and all of
`Chat` (whose entries the views read as copies), are plain fields; `ArchitectureTest` names the two classes. The
entries of the selections of blocks and palettes (`StylePresetItem`, `ColorStyleItem`) live in `ui.component`. A
generation changes the chat entry and its images only through `service.generation.FxModel`, the one class of
`service` that uses JavaFX (it also hands the notifications of a generation to the JavaFX thread). `service.chat`
itself needs no JavaFX: the `ChatLibrary` changes these models on the calling thread (see "Threading").
`LibraryEvents` calls each listener through the executor it was added with; the views add theirs with
`ui.support.FxLibraryEvents`, which passes `Platform::runLater`.

## Threading

- **JavaFX application thread**: all scene graph work, the controllers, dialogs and cells. Nothing blocking runs
  here: no HTTP, no database query of noticeable length, no image decoding of full-size files.
- **Start**: `MoodframeApplication.start` reads the one `ApplicationConfig` (a properties file), hands it to the
  splash, which places itself by the saved bounds of the main window, shows the splash and starts the thread
  `MoodFrame-startup`, which prepares everything else that needs no JavaFX thread: the registration of the
  configuration with logging, certificates and language, the theme the system prefers while the user chose none
  (`ThemeManager.getSystemPreferredTheme` runs `defaults`, `reg` or `gdbus` for up to 2 s; "Use System Theme" in
  the settings asks on a thread of its own as well), the database with its migrations (the Java data
  migrations included), the registration of the services, their start (see "Dependency injection"), the trash
  cleanup, then the loading of the chats
  (`ChatLibrary.load`, which checks every image file and repairs what a restart left behind), the thumbnails of older
  images and the check of the accounts, and it creates the services the main window looks up. The cleanup runs before
  the chats load, so they hold nothing it removed. The splash stays until the preparation is done, at least one
  second; then the JavaFX thread loads the FXML of the main window, which finds the chats loaded, applies the theme,
  shows the stage and starts the MCP server if enabled. A failed start shows its error in a message box and ends
  the application.
- **Chat models**: `ChatLibrary` keeps the chats in a plain list. The lists of the models (the chats, the entries of
  a chat, the images of an entry) are shared by several threads, so one rule holds, documented in
  `service.chat.ModelLock`:
  - they are changed only under the `ModelLock` (`change`, `changeAndGet`): by `ChatLibrary` on the calling thread
    (an MCP thread, the worker of the window, the store thread of the generation), by a generation on the JavaFX
    thread through `FxModel`;
  - every other access reads them under the lock, the JavaFX thread included: `ChatLibrary.read` for the chats,
    `ModelLock.read` for a value computed from them (e.g. the cost of a chat), and the snapshots
    `ChatLibrary.entriesOf(chat)` and `ChatLibrary.imagesOf(entry)`, unmodifiable copies to iterate. Nobody iterates
    a live list outside the lock.

  Only work in memory runs under the lock, and nobody holding it waits for another thread, so the JavaFX thread
  waits at most for such work and the MCP server never waits for the JavaFX thread. The views learn of a change
  through `LibraryEvents`, the sidebar keeps a list of its own that follows the chats of the library, and the
  listeners the views put on the models hand their work to the JavaFX thread. Nothing of the scene graph follows a
  model directly: the entry card sizes its tiles from a count of the images it keeps on the JavaFX thread, not from
  the live list, which the worker of the window and the MCP threads change.
- **Background work**: provider calls, polling of BFL jobs, downloads and thumbnails run on executors and return
  `CompletableFuture`s; results go back to the UI with `Platform.runLater`.
- **Database writes** run off the JavaFX thread, as a write may wait up to the busy timeout of 5 s for one of the MCP
  server: a generation on its own worker (see below); the chats, the trash and the groups Mood and Frame on the worker
  of the main window (`ui.panel.PanelWorker`: moods, frames, building blocks, palettes and favorites of the lists, and
  the "Manage…" windows of the building blocks, moods and frames, which wait meanwhile); the backup, its import and
  the reset of the settings on a thread of their own (`ui.controller.settings.SectionTask`), their buttons disabled
  meanwhile; the brainstorm on its session queue and its window's worker (see "Brainstorm"). The panel shows the
  outcome afterwards, a saved mood or frame as chosen. Reads that feed what is shown at once (the lists of the panel and of
  the "Manage…" windows, the fields of a chosen mood) stay on the JavaFX thread: they are short, and SQLite in WAL mode
  lets them read beside a running write. Which moods have a block in the trash (up to five queries per mood) the
  worker finds once per load of a list, in the panel and in the "Manage…" window of the moods; the rows and the
  warning of the chosen mood follow.
- **Network I/O**: blocking provider calls and downloads run on `net.IoExecutor`, a bounded pool of daemon threads,
  never on `ForkJoinPool.commonPool()`. The futures of a provider complete on such a worker (see
  `service.provider.ImageProvider`); `MoodframeApplication` shuts the pool down on exit. No task of the pool waits
  for another task of it (a full pool would wait forever): the brainstorm runs a turn, its tool calls and the naming
  of a session on virtual threads of its own (`brainstorm-N`), which wait for the provider calls on the pool, and the
  analysis of an image into a mood composes the answer of the language model instead of waiting for it. The
  composition root hands the pool to the provider clients and the generation in their constructors; the one-argument
  constructors of the clients (for tests and tools) take `IoExecutor.get()` themselves, and a few services still look
  it up: `UpdateCheckService` and `YouTubeInfoService` for their requests, and, as known deviations that put disk and
  CPU work on the network pool, `ImageAttachmentService` (reading and encoding an attachment) and
  `MoodAnalysisService` (preparing the image).
- **Disk work of the views**: the views read and decode pictures, write and compare attached files and run ffmpeg
  and the other programs of the desktop on `ui.support.DiskWorker`, a pool of at most four named daemon threads
  (`moodframe-disk-N`), never on the JavaFX thread or on `ForkJoinPool.commonPool()`.
- **Brainstorm**: `BrainstormService` queues the requests of each session; a request reads and writes the session
  (its title from the first message, the messages, the draft) on a thread of that queue, after the request before
  it, and "draft adopted" and a rename are stored on the same queue. The queue (`BrainstormQueue`) belongs to the
  shared `BrainstormSessions`, so it holds for every window: a request still running after its window closed holds
  back the next one of the session, a window opened meanwhile shows its answer when it arrives (`pending`), and
  deleting the session in any window stops it. The short name of a session is asked for on a thread of its own and
  stored on the queue only while the title is still the first message (compare and set), so a rename meanwhile wins.
  `sendUserMessage`, `sendEvent`, `noteAdopted` and `renameSession` only queue, so the JavaFX thread may call them.
  The brainstorm window lists, opens, renames, moves and deletes the sessions and creates a new one on its own worker
  (`BrainstormWorker`, one thread that ends when idle) and takes the results back with `Platform.runLater`; nothing
  of it touches the database on the JavaFX thread.
- **Generation**: `service.generation` changes the chat entry and its images on the JavaFX thread through `FxModel`
  and stores them afterwards on a single worker of its own (`ChatEntryTracker.saveImage`, `saveEntry` on the store
  thread of `GenerationWorkers`), so the writes keep the order of the changes and never run on the JavaFX thread;
  requests, downloads and files run on the I/O executor, the BFL polling on the named daemon threads of
  `GenerationWorkers`. The listeners learn of an entry that ended (completed, failed, cancelled) once its write is
  done, which the worker runs after those of its images, so the image counts the window queries then include it.
  `GenerationService.submit` may run on the JavaFX thread: it checks the order in memory (a refused order throws at
  once; only the copy of the mood looks up a palette without id by its name, a short read), adds the new entry to
  its chat and the database on the store thread and dispatches the request only then; the window shows the entry at
  once, the MCP tool `generate` waits until it is stored. A cancellation completes once the cancelled entry is
  stored. Deleting an entry or moving a chat to the trash (`ChatLibrary.deleteEntry`, `moveToTrash`) first cancels
  the generations of its entries still running and waits until the writes of their generation are done
  (`ChatLibrary.Generations`, implemented by `GenerationService.settle` and handed in by the composition root), so the
  trash books the cost the cancellation settled, for the window and the MCP server alike; so does deleting an image
  the generation still makes, or the last one it made (`ChatLibrary.deleteImage`), while a finished image deleted
  beside running ones leaves the generation alone and books what it cost itself, which the entry keeps in its cost
  (`ChatCost.imageShare`). The MCP tools `delete_entry` and `delete_image` still refuse a running entry. The generation stores the changes of its entry with
  `ChatRepository.updateChatEntry`, which leaves an entry in the trash (on its own or with its chat) as the trash
  booked it and never inserts one removed for good; an image it still delivers is dropped (see "Persistence").
- **Library events**: services report every successful write to `LibraryEvents`; each listener runs on the
  executor it was added with, never in the middle of the write that caused it. The views add theirs with
  `ui.support.FxLibraryEvents`, so they run on the JavaFX thread, later in its queue. This is how the
  main window shows changes made by the MCP server. The main window queries what it shows on a worker of its own;
  a burst of events gives one query. The entries of an open group and whether a restored item was an image are
  queried there as well, and the trash window lists, restores and deletes on it, its buttons disabled meanwhile.
- **Settings**: values that change with every pixel of a drag (divider, window bounds, tile size) are written by a
  writer thread after 500 ms of quiet and at the latest on shutdown; every other setting is written at once.
- **MCP server**: each request runs on a virtual thread of its own (`mcp-server-N`), never on the JavaFX thread, and
  reads and changes the chats without waiting for it (only `cancel_generation`, and `delete_chat` for a chat with a
  running entry, which it cancels first, wait for the change of the generation). `generate` blocks its thread up to
  `wait` seconds for the result; as every request has its own thread, no number of waiting generations keeps another
  call (`list_chats`, `cancel_generation`) from its answer.
- **Images**: `ui.support.ImageCache` holds the decoded thumbnails by path, bounded by pixels; the main window
  creates one and hands it to the cards and the images view with `ImageSources`, the memory display of the status bar
  counts its images. A file is decoded on the calling worker, once even when several ask for it at the same time;
  other files load meanwhile, and no lock is held while a file is read. `service.chat.ThumbnailManager` only creates
  and finds the thumbnail files. The full view decodes a full-size image in the background (`new Image(url, true)`),
  keeps the image shown before until it is done and then fits zoom and pan to its pixel size; the previews of the
  attachments and of "Mood from image" load in the background as well.
- **Views and the chat models**: a view reads the entries and images of a chat as a copy taken under the lock
  (`ChatLibrary.entriesOf`, `imagesOf`), and a list listener copies what it needs out of the change on the thread of
  the change before it hands its work to the JavaFX thread.
- **Language**: a view registers with `ui.support.FxI18n` and leaves when it closes: a dialog or a window with
  `FxI18n.addChangeListener(listener, dialog)` or `(listener, node)`, which removes the listener when it is hidden,
  the main window in its `dispose()`; a closed dialog stays reachable from nowhere (`SettingsDialogTest` checks the
  number of listeners).

## Dependency injection

`config.ServiceFactory` is a singleton **service locator**, not a DI container. `getService` fails with an
`IllegalStateException` when nothing is registered for a type, so a missing registration shows at the lookup; a
service the caller can do without (the check of the accounts, the update check, the trash cleanup) is looked up with
`findService`, an `Optional`. The generation and the thumbnails are required: their providers create them or fail.
`MoodframeApplication` is the composition root: it registers the instances and lazy providers at startup and hands
every service its dependencies in the constructor, which is also how tests pass mocks: the provider clients take
`ApplicationConfig`; the library services (`PromptBlockService`, `ColorStyleService`, `MoodService`,
`FrameService`, `FavoriteService`) take their repository, which `MoodframeApplication` creates on the one
`DatabaseManager`, and the one `LibraryEvents` (tests use `TestLibraryServices`); `ChatHistoryService` takes
`ChatRepository`, `ChatTrashRepository` and `ChatGroupQueries`; `ConfigurationExporter`
takes the registered library services, so an import reaches the main window; `ThumbnailManager`, `TrashManager`,
`FileManagerService` (with the thumbnails it deletes) and the MCP `ChatTools` take their folders from `LibraryPaths`;
`MoodAnalysisService` is registered once, `BrainstormService.Factory` creates a brainstorm service for each window on
the shared `BrainstormSessions` (the stored sessions and the queue of each session), `BrainstormModels` (the
language model of a session) and `BrainstormTools` (the tools of the assistant, with the one `YouTubeInfoService`).
`RequestResponseManager` takes its infrastructure: the `ChatEntryTracker` on the store thread of the one
`GenerationWorkers`, the `ImageFileStore` with a download client of the one registered `net.HttpClients`, and the
I/O executor; it builds its flows (`JobPollingFlow`, `SynchronousImageFlow`, `VideoFlow`) on them.
No class outside `ui` and the root uses the locator (`ArchitectureTest`). `MoodframeApplication.initializeApplication`
registers them in a few wiring methods (`setUpConfiguration`, `registerLibrary`, `registerProviders`,
`registerChats`, `registerGeneration`, `registerMcp`, `registerBrainstorm`); a rule of the domain lives in the
services, not in the wiring: `MoodService.followTrash` lets the moods lose the blocks deleted for good in the trash.

Creating a service reads and writes nothing. What a service does once per program start is its `start()`, which
the startup calls after the registration: `PromptBlockService`, `ColorStyleService` and `MoodService` give a block,
palette or mood of the user that is named like a standard one the next free name, as a new version may bring new
standard names (so this is no one-time migration); the moods last, as they refer to blocks and palettes. The
`TrashCleanupService` starts its cleanup every 6 hours in `start()`, which the startup calls when it creates it. The
folders of the thumbnails and the trash are created by the first file written there, not by `ThumbnailManager` or
`TrashManager`; so are the database (`DatabaseManager`), the logs (logback) and the configured images folder
(`ImageFileStore`), while the configuration creates only `~/.moodframe` for its file.
`net.HttpClients` and its `CorporateProxyConfig` read the proxy settings and the certificate folder
on their first use; the start creates that folder (`CertificateStore.createDirectory`), so the user finds it.

The locator is left to the coordinators of the user interface: `MainWindowController` and `SettingsController` look
up the services once and hand them to their parts, which hand them on to the dialogs and components they open
(`ArchitectureTest`): the managers and editors of moods, frames, blocks and palettes take their services in the
constructor, the trash windows open through `TrashWindow.Opener` (with the worker of the window), the entry cards
and the images view get the
thumbnails, the decoded thumbnails and the size check as `ImageSources`, the full view and the brainstorm dialog the configuration.
`MoodframeApplication` hands `ThemeManager` the configuration at the start.

## The main window

`MainWindow.fxml` holds the header (version, update hint, info, settings) and includes its parts with `fx:include`;
each part has an FXML file and a controller of its own in `ui.controller`, the groups Mood and Frame in `ui.panel`:

| Part                  | Controller                  | Shows                                                         |
|-----------------------|-----------------------------|---------------------------------------------------------------|
| `LibrarySidebar.fxml` | `LibrarySidebarController`  | the chats, or the moods or frames as groups of entries; holds |
|                       |                             | the selection (current chat, open group)                      |
| `ChatView.fxml`       | `ChatViewController`        | chat header, entries as chat or images, full view, download   |
|                       |                             | and delete; includes `InputBar.fxml` (`InputBarController`)   |
| `panel/MoodFramePanel`| `MoodFramePanelController`  | the groups Mood and Frame of the right panel (`ui.panel`)     |
| `GenerationPanel.fxml`| `GenerationPanelController` | model, provider fields, count, attachments, price             |
| `StatusBar.fxml`      | `StatusBarController`       | status message, memory, API key status                        |

`MainWindowController` is the coordinator. It looks up the services in `ServiceFactory` once and hands them to the
parts with an `init` method (the `FXMLLoader` creates the controllers, their `initialize()` sets up only their own
nodes); a part never asks the locator. The window has one worker thread for its blocking work (queries of the groups,
downloads, deletes, the writes of the groups Mood and Frame), which the sidebar, the chat view and the panel get as
an `Executor`. A part reports back through a listener
interface of its own (`LibrarySidebarController.Listener`, `ChatViewController.Actions`,
`GenerationPanelController.Listener`, `MoodFramePanelController.Listener`, `InputBarController.Listener`) and shows
messages through `ui.support.StatusSink`, where an error is told by the caller, not read from its text. The
coordinator carries
out what spans parts: sending, the brainstorm, reusing and editing an entry, the changes of the library, the trash and
the running generations. On a change of the language it calls `updateTexts()` of every part, which relabels itself.
`PanelGroups` folds the three groups of the right panel, `CornerCloud` draws the cloud behind the window.

The sidebar shows the chats from a list of its own, which follows `ChatLibrary.chats()` after its own changes and
on every reported change of a chat, removing, inserting and sorting only what differs so the selection stays. When
the window is hidden, `MainWindowController.dispose()` lets go of what it registered beyond itself: its listeners of
the language (its own, the full view's and the cards'), of the theme, the generations, the library events, the trash
and the account checks, the timelines of the memory display and the clipboard watch, and its worker; a closed window
stays reachable from nowhere.

`ui.panel.MoodFramePanelController` is self-contained, so that another window can show the same fields; the
brainstorm dialog, which `ui.panel` lies below, may reuse it in its result form, which is planned (ticket 136) and not
done yet. It takes its services as `MoodFramePanelController.Services` (with the trash) and the worker that writes the
library, follows the chosen model with
`followModel`, tells its choice with `selection()` and `promptBlocks()`, takes a choice with `chooseMood`,
`chooseFrame`, `setAspectRatio`, `setResolution` or from a chat entry, and reports every change of a field with
`Listener.changed()`; its host passes the changes of the library (`onLibraryChanged`) and of the language on. It
keeps the moods and hands the rest to parts on its nodes: `BlockChoices` (the building block fields with their
favorites, locks and dialogs) and `FrameGroupPart` (frame, aspect ratio, resolution and the hints of the model).
`GenerationPanelController` likewise hands the attached images to `GenerationAttachments` and the fields of BFL and
Gemini to `ProviderParameters`; `ChatViewController` its full view with the steps from entry to entry to
`EntryFullView`; `ChatEntryCard` loads the pictures of its tiles with `EntryTilePictures` and builds its context
menus with `EntryMenus`; the full view (`ImagePreviewOverlay`) plays a video with `VideoPlayback`. A cell of the chat
keeps its card while it shows the same entry: the card follows status, prompt and images of its entry itself, and a
change a generation reports reaches only the card of its entry (its cost, the tile of a changed image), so no other
card is built anew and an open context menu stays.

## Settings dialog

`SettingsDialog.fxml` holds only the list of sections and includes one FXML per section from `fxml/settings`
(`fx:include`): the BFL, Gemini and OpenAI tabs, proxy and certificates, appearance, language, advanced (logging,
backup, reset) and MCP. Each has a controller in `ui.controller.settings` that implements `SettingsSection`: it
prepares its controls, loads its values (at the opening and after an import), shows them after a reset, saves them
on "Save" and relabels its texts. `ui.controller.SettingsController` is the coordinator: it looks up the services once
in the `ServiceFactory` and hands them to every section as `SettingsServices` (configuration, `ProviderRegistry`,
`ApiProviderValidator`, `McpService`, `ConfigurationExporter`, building blocks, `SettingsPrompts`), switches the
sections, and passes "Save", reload, reset and a change of the language on to all of them; the main window calls
`applySettings()` when the dialog closes. Every connection test runs through `ConnectionTest.run` on a worker and
tests a typed key with a copy of the live client (`ImageProvider.testConnection(apiKey, endpoint)`), so neither the
live client nor the saved key changes until "Save". A status shown from an i18n key keeps the key, so a change of the
language relabels it. The dialog follows the language until its window closes. `SettingsPrompts` wraps the message
boxes and file choosers, so `SettingsDialogTest` clicks through the dialog off-screen.

## Brainstorm window

`ui.brainstorm.BrainstormDialog` composes three parts, like the main window: `BrainstormHistory` (the stored
sessions: open, rename, move, delete, new chat), `BrainstormChatView` (title, steps, messages with the research and
the linked videos, quick replies, input and the choice of the language model) and `BrainstormResultForm` (the draft
of the shown chat mapped onto the lists of the main window, the checkboxes of the adoption and "Adopt"). Each part
reports through a listener interface of its own; the dialog keeps one `BrainstormChat` per session, so an answer
lands in the chat that asked, sends the requests and hands each answer to the chat view and the form. The result
form is the place where the groups of `ui.panel` can replace the fields of the mood and the frame (ticket 136).

## Folders and settings

- **Folders**: `config.LibraryPaths` is the one place that knows where the library lives: `~/.moodframe` with
  `config.properties`, `secret.key`, `db/`, `images/`, `thumbnails/`, `trash/`, `logs/`, `certificates/`, and the
  user's `Downloads` and `Documents`. `MoodframeApplication` creates it once from `user.home` and hands it to the
  one `ApplicationConfig`, which the splash and the services share; `ApplicationConfig.paths()` returns it with the
  images folder of the setting `path.images` (whose default is `~/.moodframe/images`), and that instance is
  registered for the services. The database, the
  logs and the images of a generation come from these paths. The only folder setting is `path.images`: former
  versions also wrote `path.database` and `path.logs`, which no dialog ever offered; they are ignored and no longer
  written. Tests use the paths of a temporary home. Only `logback.xml` still reads `user.home` itself, because
  logging starts before any code.
- **Settings**: `ApplicationConfig` sits on one `ConfigStore` (the properties file, the immediate and the delayed
  writing, the secrets) and groups the settings in sections: `ui()` (`UiState`: theme, language, window, dividers,
  selections, the resolution of each model family), `generation()` (`GenerationDefaults`: model, frame, building
  blocks, model parameters, the duration of a video),
  `accounts()` (`ProviderAccounts`: key, validation, activation, chat checks keyed by `ApiProvider`, the BFL and
  OpenAI extras, the brainstorm engine), `proxy()` (`ProxySettings`) and `mcp()` (`McpSettings`). The property keys
  are those of all former versions, so an existing file loads unchanged. `ApplicationConfig` itself keeps the
  folders, detailed logging, the update hint, the reset and a few shortcuts for the provider clients and the
  generation; there is no access by raw key, every caller reads and writes the typed methods of the sections. A file
  that cannot be read is logged and copied to `config.properties.unreadable-<time>` before the defaults are written
  over it (if the copy fails, nothing is written in that session); the main window tells the user once where the copy
  is. The reset holds the lock of the writing, so the delayed writer never writes the file without the secrets, and
  replaces all values at once, so a reader on another thread sees the settings before or after it, never half reset
  (`ConfigStore` documents the thread safety).
- **Secrets**: the API keys, the MCP token and the proxy password are the constants of `config.Secret`; each comes
  from an environment variable, a system property or, encrypted, from the file. A reset of the settings keeps all
  of them.

## Persistence

- **SQLite** in `~/.moodframe/db`, accessed with **plain JDBC** (`java.sql`), no ORM. The repositories get their
  connections from `database.DatabaseManager`, with foreign keys on and a busy timeout. The chats have three:
  `ChatRepository` (chats, entries, images and their mapping), `ChatTrashRepository` (their trash with the booking
  of the deleted costs) and `ChatGroupQueries` (the entries grouped by mood and by frame, read only).
- **Transactions** begin IMMEDIATE (`DatabaseManager.inTransaction`): a write that depends on a read reads inside
  the transaction, guards its update (`AND deleted_at IS NULL`, checked row counts) and moves files only after the
  commit. Every move to the trash and back is guarded this way, for chats, entries, images, moods, frames, blocks
  and palettes, and so is every removal for good: it removes only rows still in the trash
  (`AND deleted_at IS NOT NULL`) and answers false otherwise, so an item restored by the main window or an agent
  after the cleanup listed it survives with its entries, images and files. The one exception is the reset of the
  settings, which removes the style blocks of the user for good, in use and in the trash
  (`PromptBlockRepository.deleteAll`), as its confirmation says; the moods lose their references to them in the
  same transaction.
- **Files of the trash**: `ChatTrashRepository` is the one owner of the files of a deleted, restored or removed chat,
  entry or image: after the commit it hands `repository.ChatFiles` the images whose files follow its change (those its
  transaction deleted, restored or removed; of a chat the images in use, as those deleted on their own lie in the
  trash already, and all of them when it is removed for good), so an image another caller deleted before is not moved
  twice and a row that was not deleted keeps its file. Its writes run one after the other, each with the moves of its
  files, so a deletion and an immediate restore from another thread move the files in the order of their commits. An
  image a generation delivers after its entry or chat went to the trash is not saved
  (`ChatRepository.saveGeneratedImage` answers false); its file, written into the folder of the images in use, is
  removed by `ChatTrashRepository.dropLateImage` the same way, after the writes before. An image whose entry is not
  stored at all (its insert failed) keeps its file and fails. The services and the other repositories never move or
  remove these files themselves.
- **Time columns** of the chats, entries and images (`created_at`, `deleted_at`, `chats.updated_at`), of the
  brainstorm sessions (`created_at`, `updated_at`; a session has no trash and is deleted for good) and the
  `deleted_at` of moods, frames, blocks and palettes hold epoch milliseconds of the local time (`setTimestamp`);
  `ChatRepository` still reads the older text values of `deleted_at`, and one it cannot read counts as deleted now
  (logged), so the row stays in the trash. The `created_at` and `updated_at` of moods, frames, blocks and palettes are
  still SQLite's `CURRENT_TIMESTAMP` (UTC text, from the column default and the updates); nothing reads them.
- **Model of an entry**: `chat_entries.model` holds the fixed id of the model since 2.1.3
  (`GenerationModel.entryModelOf`: the constant name, `OpenAI · <id>` for a model of the gateway); older rows keep
  the English display name with price and tagline, which `GenerationModel.fromEntryModel` still reads. They are not
  migrated, as the display names of former versions are not all known. The views show `ModelLabels.entryName`, the
  MCP server `GenerationModel.entryDisplayName` with the id of `GenerationModel.idOf`.
- **Finders** of one row answer with an `Optional` in every repository. The finders of lists skip the rows in the
  trash unless named for it (`findDeleted`, `findDeletedChats`), and so do the finders of one mood, frame, block or
  palette (`findInTrash` and `exists` look into the trash); the finders of one chat, entry or image by id
  (`ChatRepository.findChatById`, `findChatEntryById`, `findGeneratedImageById`) answer a row in the trash as well,
  with its `deleted_at`, as the trash, its restore and a deletion look them up by id. Each finder's Javadoc says
  which rows it answers.
- **Error contract**: repositories throw `SQLException` and never swallow a database error, so "not found" stays
  apart from "the database failed". The services decide: most log the error and answer with a fallback (false, null,
  empty) through `service.library.RepositoryAccess`.
- **Migrations** are SQL files `V###__description.sql` in `src/main/resources/db/migration`, applied in order at
  startup and tracked in a migration table; a data migration that needs Java (`FluxCostBackfill`,
  `DeletedCostCorrection`) runs in the same sequence and carries the prices and formulas it books with (frozen at
  2.1.3), so a later version books the same. A migration runs only when it is listed in `MigrationRunner.MIGRATIONS`;
  it is applied and recorded in one transaction, which reads the record again under the write lock, so of two
  instances of the application starting on the same library at once only one applies it. A legacy library (tables, but
  no records) gets each migration statement by statement: a statement whose table, index, trigger or column is already
  there is skipped, the others are applied, and the migration is recorded once all are applied or skipped. A real
  failure always surfaces: on the legacy path and in the repair of a V011 that was recorded without its columns, which
  adds only what is missing, in one transaction.
- **Triggers**: only the favorites cleanup of V027. V031 dropped V001's triggers on `chats.updated_at`, which never
  ran in older libraries; `ChatLibrary` sets `updated_at` itself and the chat list is ordered by `sort_order`.
- **Soft delete**: chats, moods, frames, building blocks and palettes get `deleted_at`; the central trash
  (`service.trash.TrashCenter`) restores them or removes them for good. Image files wait in a trash directory.
  Brainstorm sessions have no trash: deleting one removes it with its messages and drafts.
- **Former columns**: `chat_entries.guidance` and `generated_images.guidance` hold the safety tolerance of the
  generation (the column of the former FLUX guidance, reused), and `steps` is always written as 0; `ChatRepository`
  maps them.
- **Files**: images and videos live in the images folder of `LibraryPaths` (by default `~/.moodframe/images`),
  thumbnails under `~/.moodframe/thumbnails`; the database stores their paths.
- **Settings** are a properties file `~/.moodframe/config.properties`, not part of the database (see "Folders and
  settings").

## Decisions

### D1: SQLite in WAL mode with a busy timeout

The MCP server writes to the library while the UI reads and writes it. In the default rollback journal a writer
blocks readers and parallel writers fail with `SQLITE_BUSY`. `DatabaseManager` switches the database to WAL once
(the mode stays with the file) and opens every connection with a busy timeout, so readers and a writer do not wait
for each other and concurrent writers queue up instead of failing (`ParallelWritesTest`). Transactions begin
IMMEDIATE (`transaction_mode`): a deferred transaction that reads first cannot wait for the write lock later and
fails with `SQLITE_BUSY_SNAPSHOT`. Reads in auto-commit mode take no write lock and are not serialized.

### D2: MCP server on 127.0.0.1 with a bearer token, off by default

Local AI agents such as Claude Code may read and change the library and start paid generations. The server binds
only to 127.0.0.1, requires `Authorization: Bearer <token>` on every request, rejects foreign `Host` and `Origin`
headers against DNS rebinding and never logs the token. It is disabled until the user enables it in the settings.
It is an own small implementation on `jdk.httpserver`, because the MCP Java SDK needs a servlet container, Reactor
and Jackson 3.

### D3: API keys encrypted with a random key per installation

API keys are stored AES-256-GCM encrypted (`config.EncryptionUtil`) with a random 256-bit key generated at the first
use in `~/.moodframe/secret.key` (`LibraryPaths.secretKey()`), readable only by the user. A copied
`config.properties` alone reveals no key, and no key is derived from constants in the code. `util.Secrets` keeps
keys and tokens out of logs and messages.

### D4: Provider clients behind interfaces, everything else concrete

`BflApiService`, `GeminiApiService`, `OpenAiApiService` and `RequestResponseService` are interfaces because tests
replace them with mocks and fake HTTP servers. Interfaces with a single implementation and no consumer typed against
them were removed; a new interface is introduced when a second implementation or a test seam needs it.

The three provider clients extend the provider SPI `service.provider.ImageProvider` (key, connection test,
a copy with other credentials for the test of an unsaved key, submission of images and videos, seeds, attached
images, prompt limit, the parameters a model takes, download headers, the jobs of a polled generation) and keep their
own extras, such as the brainstorm chat. The generation knows no provider by name: what differs per provider lives in
its client; a video model is submitted like an image model, and images and videos are downloaded on one path
(`ImageFileStore`). A provider that does not answer with the image (`answersWithImage()` false, today BFL) goes
through `service.generation.JobPollingFlow`: one job per image, whose request the provider limits (`prepareJob`, e.g.
the range of the seed), one retry with the safety tolerance the account is approved for
(`approvedSafetyTolerance`), polled with `pollJob` one answer at a time and priced with `reportedCost`. A provider
that answers with the image goes through `SynchronousImageFlow`, which adds the costs the provider names beyond the
price per image (`extraCosts`, Gemini: Google Search and thinking tokens); a video model through `VideoFlow`. A
provider with seeds (`usesSeeds()`) gets random ones from `service.generation.Seeds`, the images of one without seeds
store none. Remaining assumptions of the polled flow: the statuses `pollJob` answers are those of BFL (named in its
Javadoc), a 404 means "not visible yet", and an approved safety tolerance is stored as the FLUX.2 setting. A model id
that is neither a constant nor served by a provider goes to BFL (`ProviderRegistry.providerOf`), whose former FLUX
models are no constants any more. The prompt limit of a model comes from its provider (`ProviderRegistry.promptLimit`),
for the generation and the MCP tools alike. The parameters of the models (name, kind, limits, default, slider step)
are one table, `model.ModelParameters`, which the MCP schema, the MCP validation and the providers read. A request is
checked before it is sent by the provider of its model (`ImageProvider.checkRequest`, in the dispatch and in the BFL
client): the limits every provider shares come from that table and `GenerationModel` (image count, guidance, steps,
the video lengths of the model, an input image as `checkImageInput` decides from `supportsImageAttachment`, the
prompt limit), and a provider adds in its override what only its models have (BFL: width and height of FLUX.2 and the
former FLUX models, by `ImageDimensions`); nothing guesses the provider from the model id.

Models beyond the constants are those a provider lists (`listedModels()`, today the models the user chose of the
OpenAI-compatible gateway); they share the placeholder constant `GenerationModel.OPENAI_IMAGE` and are named
`OpenAI · <id>` in the dropdown (`GenerationModel.OPENAI_PREFIX`). The dropdown and the MCP tools offer them from the
registry (`GenerationService.availableItems`), the same source `ProviderRegistry.isOrderable` accepts them from.
`GenerationModel.constantOf` tells a constant from such an id, `ProviderRegistry.isOrderable` and
`ProviderRegistry.apiModelId` decide for the generation, so no class of `service` but the OpenAI client names the
placeholder (`ArchitectureTest`). A second provider with listed models would need an item prefix of its own.

Errors follow one rule: every failure of a provider or a generation the user reads is a `ProviderException` with an
i18n key and its arguments (`ApiHttpException` with the HTTP status), or carries one: a job a provider answers as
failed (a blocked image, a video without file) holds it as `GenerationJobInfo.getFailure()`, and the generation
wraps its own failures (timeout, moderated or failed job, download, file, attachment, cancel) the same way. A text of
the provider (e.g. the refusal of Omni) is a `ProviderException` with that fixed text. The entry and its images
store the message once, rendered by `ChatEntryTracker.errorText` in the language of whoever placed the order: the
language of the user interface for the window, English for the MCP server. No image or video client and no class of
the generation renders a message with `I18nManager.getText` itself (`ArchitectureTest`, besides the dropdown items of
`ModelLabels`).

`MoodframeApplication` builds one `ProviderRegistry` and passes it to the services that dispatch by provider
(`RequestResponseManager`, `GenerationService`, `ApiProviderValidator`); every constant of `GenerationModel` names
its `ApiProvider`. What needs no change for a new provider: the dispatch and the flows of the generation, the
dropdown and the models of the MCP tools (`ModelLabels.availableItems` lists the selectable constants and the listed
models of every registered provider the user can use), the startup check
of the accounts (`ApiProviderValidator` tests the connection of every registered provider; only Gemini and OpenAI add
the checks of their brainstorm language model) and the MCP tools, as long as its models take only the request options
there are. A new provider is, in this order:

1. its constant in `model.ApiProvider` (display name, prefix of its account settings in `ProviderAccounts`, largest
   attachment) and its key in `config.Secret`, mapped in `ProviderAccounts.secret` (and `isBrainstormAvailable`,
   false without a language model);
2. its client in a subpackage of its own, `service.provider.<name>` like `bfl`, `gemini` and `openai`, which uses
   `service.provider` and no other client (add it to `SERVICES` and `VENDORS` of `ArchitectureTest`): an interface
   extending `ImageProvider` with its implementation (prompt limit, `prepare`, the flow it answers with, seeds, costs,
   download headers, and `checkRequest` or `checkImageInput` only for a limit its models have beyond the table, such
   as a model it lists that takes no image), created in `MoodframeApplication.registerProviders` and added to the
   `ProviderRegistry` there;
3. its models as constants of `model.GenerationModel`, which decides several things by provider: the branch of the
   constructor for their resolution (`maxMegapixels`, `maxResolution`; `supportsImageAttachment` is true for every
   provider but BFL), `supportsMultiImage` and `getMaxInputImages` for their reference images (false and 0 for a new
   provider: the dropdown then drops its models as soon as an image is attached,
   `GenerationService.availableItems(config, imageCount)`, `get_capabilities` reports no reference image and
   `mcp.GenerateTools.references` refuses any), `supportsGroundingSearch` and `supportsGoogleSearch` (Gemini, read by
   `ModelParameters` and `GenerationCost.search`), `supportsSafetyTolerance` (BFL), and `getFamilyName` and
   `getResolutionLabel` for variants; their parameters in `model.ModelParameters`, their prices in
   `model.GenerationCost` (`GenerationCost.isKnown` answers false only for OpenAI, whose prices are unknown, so a
   provider without known prices needs its branch there), their aspect ratios in `AspectRatios.forModel` (a branch per
   provider, BFL's otherwise), the multiple of their sizes in `ImageDimensions.multipleFor` and, for a model sized by
   width and height, its sides in `ImageDimensions.minimumFor` and `maximumFor`, and the video lengths of a video
   model in `getVideoDurationChoices`, which the MCP schema and the check of the request read;
4. a request option its models take beyond the general ones: a field of `GenerationOrder.Parameters` and of
   `service.provider.RequestOptions` (filled in `GenerationService.createRequest`), its field in
   `ui.controller.ProviderParameters`, and its value in `mcp.GenerateTools.parameters`, which builds the
   `GenerationOrder.Parameters` of the MCP tool `generate` from the parameters of `model.ModelParameters`;
5. the user interface of the account: a settings tab (FXML in `fxml/settings`, a `SettingsSection` in
   `ui.controller.settings`, its client in `SettingsServices`), its line in `StatusBarController` and its texts in
   the i18n resources (`status.request.submitted.<last part of the prefix>`, falling back to a generic one).

### D5: Detailed logging as a log level

The setting "detailed logging" sets the logback logger `art.moodframe` to DEBUG and back to the configured level
(`config.DetailedLogging`), at startup and on every change. Code logs details with `logger.debug(...)` instead of
asking the setting, and guards a costly argument with `logger.isDebugEnabled()`. Only the startup, the settings
dialog and the configuration export read `getLoggingEnabled()`.

### D6: A conversation with a language model in the format of no provider

The brainstorm and the analysis of an image talk to Gemini or to an OpenAI language model, as
`service.brainstorm.ChatModel` chooses. They hold the conversation as `service.provider.ChatTurn`s: a turn of the
user (text and images), an answer of the model (text, or tool calls with their ids) and the results of the tools; the
tools are `ChatTool`s (name, description, JSON Schema of the arguments), which the brainstorm declares and runs. Each
chat client translates the turns into its own request (`GeminiChatClient.buildChatBody`,
`OpenAiChatClient.buildChatBody`) and its answer back, so neither client knows the other (`service.provider.gemini`
and `service.provider.openai` use no class of each other, `ArchitectureTest`). Gemini requires the thought
signatures of its tool calls back unchanged: its answer keeps its parts as `replay`, which only Gemini reads; the
answer of another model is built from its text and calls. The types, and the question of the web search check
(`ChatTurn.SEARCH_CHECK_QUESTION`), live in `service.provider`, which lies below the clients and `service.brainstorm`.
The stored messages of a session stay role and text (`brainstorm_messages`); the tool rounds of a turn exist only
while it runs, so the database kept its format.
