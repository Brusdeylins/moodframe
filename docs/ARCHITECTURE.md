# MoodFrame Architecture

MoodFrame is a JavaFX 23 desktop application (Java 21, one JPMS module `art.moodframe`). It generates images and
videos with FLUX (BFL), Gemini and OpenAI and keeps chats, moods, frames and building blocks in a local SQLite
library under `~/.moodframe`. This document describes the layers, the threading model, how services find each other,
the persistence and the main decisions. The `package-info.java` of each package lists its main classes.

## Layers

```
 ui (controller, dialog, cell, component, dnd)      mcp
            \                                      /
             +--------------> service <-----------+
                                 |
                             repository
                                 |
                              database
 ---------------------------------------------------------
 model, util, config: used by every layer above
```

| Package      | Role                                                                                   |
|--------------|----------------------------------------------------------------------------------------|
| `ui`         | JavaFX views: FXML controllers, dialogs, list cells, components, drag and drop          |
| `mcp`        | MCP server for local AI agents, a second adapter beside the UI on the same services    |
| `service`    | application logic: generation, provider clients, library, trash, files of the images  |
| `repository` | data access with plain JDBC, one repository per aggregate                              |
| `database`   | SQLite connections, WAL mode, schema migrations                                         |
| `model`      | domain data classes, records and enums                                                  |
| `util`       | general helpers: i18n, themes, images, videos, opening files and web pages             |
| `config`     | settings (`ApplicationConfig`), key encryption, the service locator (`ServiceFactory`) |

Allowed dependency directions:

- `ui` → `service` → `repository` → `database`; each layer may also use `model`, `util` and `config`.
- `mcp` → `service`, never `ui`. Whatever the UI can do to the library, the MCP tools do through the same services.
- `model` depends on nothing of the application. `util` depends only on `config` (settings such as the theme).
- No layer depends on a layer above it. Where a lower layer needs something from above, it owns an interface and
  the upper layer implements it, e.g. `repository.ChatFiles` implemented by `service.ChatFileService`.

Known deviations: some services use `Platform.runLater` to hand results to the JavaFX thread (e.g.
`LibraryEvents`, `GenerationService`), and some model classes carry JavaFX properties for binding.

## Threading

- **JavaFX application thread**: all scene graph work, the controllers, dialogs and cells. Nothing blocking runs
  here: no HTTP, no database query of noticeable length, no image decoding of full-size files.
- **Background work**: provider calls, polling of BFL jobs, downloads, thumbnails and database writes run on
  executors and return `CompletableFuture`s. Results go back to the UI with `Platform.runLater`.
- **Library events**: services report every successful write to `LibraryEvents`; listeners run on the JavaFX
  thread, later in its queue, so a listener never runs in the middle of the write that caused it. This is how the
  main window shows changes made by the MCP server.
- **MCP server**: its tools run on the threads of the `jdk.httpserver`, never on the JavaFX thread.

## Dependency injection

`config.ServiceFactory` is a singleton **service locator**, not a DI container. `MoodframeApplication` registers
the instances and lazy providers at startup; classes fetch what they need with
`ServiceFactory.getInstance().getService(Foo.class)`, often in their constructor. Some classes already take their
dependencies as constructor parameters (e.g. the provider clients take `ApplicationConfig`), which is how tests pass
mocks.

The plan is to move step by step to constructor injection with `MoodframeApplication` as the composition root, so
that dependencies become visible in signatures and the locator can shrink to the startup code.

## Persistence

- **SQLite** in `~/.moodframe/db`, accessed with **plain JDBC** (`java.sql`), no ORM. The repositories get their
  connections from `database.DatabaseManager`, with foreign keys on and a busy timeout.
- **Error contract**: repositories throw `SQLException` and never swallow a database error, so "not found" stays
  apart from "the database failed". The services decide: most log the error and answer with a fallback (false, null,
  empty) through `service.RepositoryAccess`.
- **Migrations** are SQL files `V###__description.sql` in `src/main/resources/db/migration`, applied in order at
  startup and tracked in a migration table; a data migration that needs Java (`FluxCostBackfill`) runs in the same
  sequence.
- **Soft delete**: chats, moods, frames, building blocks and palettes get `deleted_at`; the central trash
  (`service.TrashCenter`) restores them or removes them for good. Image files wait in a trash directory.
- **Files**: images and videos live under `~/.moodframe/images`, thumbnails under `~/.moodframe/thumbnails`; the
  database stores their paths.
- **Settings** are a properties file `~/.moodframe/config.properties`, not part of the database.

## Decisions

### D1: SQLite in WAL mode with a busy timeout

The MCP server writes to the library while the UI reads and writes it. In the default rollback journal a writer
blocks readers and parallel writers fail with `SQLITE_BUSY`. `DatabaseManager` switches the database to WAL once
(the mode stays with the file) and opens every connection with a busy timeout, so readers and a writer do not wait
for each other and concurrent writers queue up instead of failing (`ParallelWritesTest`).

### D2: MCP server on 127.0.0.1 with a bearer token, off by default

Local AI agents such as Claude Code may read and change the library and start paid generations. The server binds
only to 127.0.0.1, requires `Authorization: Bearer <token>` on every request, rejects foreign `Host` and `Origin`
headers against DNS rebinding and never logs the token. It is disabled until the user enables it in the settings.
It is an own small implementation on `jdk.httpserver`, because the MCP Java SDK needs a servlet container, Reactor
and Jackson 3.

### D3: API keys encrypted with a random key per installation

API keys are stored AES-256-GCM encrypted (`config.EncryptionUtil`) with a random 256-bit key generated at the first
start in `~/.moodframe/secret.key`, readable only by the user. A copied `config.properties` alone reveals no key,
and no key is derived from constants in the code. `util.Secrets` keeps keys and tokens out of logs and messages.

### D4: Provider clients behind interfaces, everything else concrete

`BflApiService`, `GeminiApiService`, `OpenAiApiService` and `RequestResponseService` are interfaces because tests
replace them with mocks and fake HTTP servers. Interfaces with a single implementation and no consumer typed against
them were removed; a new interface is introduced when a second implementation or a test seam needs it.

### D5: Detailed logging as a log level

The setting "detailed logging" sets the logback logger `art.moodframe` to DEBUG and back to the configured level
(`config.DetailedLogging`), at startup and on every change. Code logs details with `logger.debug(...)` instead of
asking the setting; the remaining `if (config.getLoggingEnabled())` gates are being converted.
