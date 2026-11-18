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
                                 |          \
                             repository     net
                                 |
                              database
 ---------------------------------------------------------
 model, util, config: used by every layer above
```

| Package      | Role                                                                                   |
|--------------|----------------------------------------------------------------------------------------|
| `ui`         | JavaFX views: FXML controllers, dialogs, list cells, components, drag and drop          |
| `mcp`        | MCP server for local AI agents, a second adapter beside the UI on the same services    |
| `service`    | application logic, split by topic into subpackages (see below)                         |
| `net`        | network access: HTTP clients with proxy and corporate certificates, the certificate     |
|              | folder (`CertificateStore`), the pool of the blocking network work (`IoExecutor`)       |
| `repository` | data access with plain JDBC, one repository per aggregate                              |
| `database`   | SQLite connections, WAL mode, schema migrations                                         |
| `model`      | domain data classes, records and enums                                                  |
| `util`       | general helpers: i18n, themes, images, videos, opening files and web pages             |
| `config`     | folders of the library (`LibraryPaths`), settings (`ApplicationConfig` and its          |
|              | sections), key encryption, the service locator (`ServiceFactory`)                       |

Allowed dependency directions:

- `ui` → `service` → `repository` → `database`; each layer may also use `model`, `util` and `config`.
- `ui` → `net` and `service` → `net`: the provider clients and the downloads build their HTTP clients with
  `net.HttpClients` and run on `net.IoExecutor`; the settings dialog manages the certificates with
  `net.CertificateStore` and tells `HttpClients` that the proxy changed. `net` uses only `config` and `util`.
- `mcp` → `service`, never `ui`. Whatever the UI can do to the library, the MCP tools do through the same services.
- `ui` → `mcp` only for the lifecycle of the server: the settings dialog starts, stops and restarts
  `mcp.McpService` and shows its command line. No view uses the tools of the server.
- `model` should depend on nothing of the application. Known exception: `GenerationModel` asks
  `util.I18nManager` for localized display names; this is to move into the UI.
  `util` depends only on `config` (settings such as the theme).
- No layer depends on a layer above it. Where a lower layer needs something from above, it owns an interface and
  the upper layer implements it, e.g. `repository.ChatFiles` implemented by `service.chat.ChatFileService`.

The subpackages of `service`, each using only those above it in the table (no cycles):

| Package              | Role                                                                                 |
|----------------------|--------------------------------------------------------------------------------------|
| `service.provider`   | provider SPI and registry, the clients of BFL, Gemini and OpenAI, account checks     |
| `service.prompt`     | the prompt text: `PromptComposer`, `CameraPromptBuilder`                             |
| `service.trash`      | the central trash (`TrashCenter`, `TrashGroup`), its cleanup, the trash directory    |
| `service.library`    | moods, frames, building blocks, palettes, favorites, `LibraryEvents`, trash groups   |
| `service.chat`       | the chats with their entries, files, thumbnails, attachments, their trash groups     |
| `service.generation` | carries out a generation order: entry, request, BFL job polling, image files         |
| `service.brainstorm` | the brainstorm chat and the analysis of an image into a mood                         |
| `service`            | `ConfigurationExporter` (settings and library backup), `UpdateCheckService`          |

`service.trash` owns the interface `TrashGroup`; the groups of chats, images, moods, frames and blocks live with their
items in `service.chat` and `service.library`, so the trash depends on none of them.

Known deviations: some services use `Platform.runLater` to hand results to the JavaFX thread (e.g.
`LibraryEvents`, `GenerationService`), and some model classes carry JavaFX properties for binding.

## Threading

- **JavaFX application thread**: all scene graph work, the controllers, dialogs and cells. Nothing blocking runs
  here: no HTTP, no database query of noticeable length, no image decoding of full-size files.
- **Background work**: provider calls, polling of BFL jobs, downloads, thumbnails and database writes run on
  executors and return `CompletableFuture`s. Results go back to the UI with `Platform.runLater`.
- **Network I/O**: blocking provider calls, downloads and the brainstorm tools run on `net.IoExecutor`, a bounded
  pool of daemon threads, never on `ForkJoinPool.commonPool()`. The futures of a provider complete on such a worker
  (see `service.provider.ImageProvider`); `MoodframeApplication` shuts the pool down on exit.
- **Library events**: services report every successful write to `LibraryEvents`; listeners run on the JavaFX
  thread, later in its queue, so a listener never runs in the middle of the write that caused it. This is how the
  main window shows changes made by the MCP server. The main window queries what it shows on a worker of its own;
  a burst of events gives one query.
- **Settings**: values that change with every pixel of a drag (divider, window bounds, tile size) are written by a
  writer thread after 500 ms of quiet and at the latest on shutdown; every other setting is written at once.
- **MCP server**: its tools run on the threads of the `jdk.httpserver`, never on the JavaFX thread.

## Dependency injection

`config.ServiceFactory` is a singleton **service locator**, not a DI container. `MoodframeApplication` registers
the instances and lazy providers at startup; classes fetch what they need with
`ServiceFactory.getInstance().getService(Foo.class)`, often in their constructor. Many classes already take their
dependencies as constructor parameters, which is how tests pass mocks: the provider clients take
`ApplicationConfig`; the library services (`PromptBlockService`, `ColorStyleService`, `MoodService`,
`FrameService`, `FavoriteService`) take the `DatabaseManager` and the one `LibraryEvents`; `ConfigurationExporter`
takes the registered library services, so an import reaches the main window; `ThumbnailManager`, `TrashManager`,
`FileManagerService` and the MCP `ChatTools` take their folders from `LibraryPaths`.

The plan is to move step by step to constructor injection with `MoodframeApplication` as the composition root, so
that dependencies become visible in signatures and the locator can shrink to the startup code.

## Folders and settings

- **Folders**: `config.LibraryPaths` is the one place that knows where the library lives: `~/.moodframe` with
  `config.properties`, `secret.key`, `db/`, `images/`, `thumbnails/`, `trash/`, `logs/`, `certificates/`, and the
  user's `Downloads` and `Documents`. `MoodframeApplication` creates it once from `user.home` and hands it to
  `ApplicationConfig`; `ApplicationConfig.paths()` returns it with the images folder of the setting `path.images`
  (whose default is `~/.moodframe/images`), and that instance is registered for the services. Tests use the paths of
  a temporary home. Only `logback.xml` still reads `user.home` itself, because logging starts before any code.
- **Settings**: `ApplicationConfig` sits on one `ConfigStore` (the properties file, the immediate and the delayed
  writing, the secrets) and groups the settings in sections: `ui()` (`UiState`: theme, language, window, dividers,
  selections), `generation()` (`GenerationDefaults`: model, frame, building blocks, model parameters),
  `accounts()` (`ProviderAccounts`: key, validation, activation, chat checks keyed by `ApiProvider`, the BFL and
  OpenAI extras, the brainstorm engine), `proxy()` (`ProxySettings`) and `mcp()` (`McpSettings`). The property keys
  are those of all former versions, so an existing file loads unchanged. `ApplicationConfig` itself keeps the
  folders, detailed logging, the update hint, raw properties, the reset and a few shortcuts for the provider
  clients and the generation.
- **Secrets**: the API keys, the MCP token and the proxy password are the constants of `config.Secret`; each comes
  from an environment variable, a system property or, encrypted, from the file. A reset of the settings keeps all
  of them.

## Persistence

- **SQLite** in `~/.moodframe/db`, accessed with **plain JDBC** (`java.sql`), no ORM. The repositories get their
  connections from `database.DatabaseManager`, with foreign keys on and a busy timeout.
- **Error contract**: repositories throw `SQLException` and never swallow a database error, so "not found" stays
  apart from "the database failed". The services decide: most log the error and answer with a fallback (false, null,
  empty) through `service.library.RepositoryAccess`.
- **Migrations** are SQL files `V###__description.sql` in `src/main/resources/db/migration`, applied in order at
  startup and tracked in a migration table; a data migration that needs Java (`FluxCostBackfill`, `DeletedCostCorrection`) runs in
  the same sequence.
- **Soft delete**: chats, moods, frames, building blocks and palettes get `deleted_at`; the central trash
  (`service.trash.TrashCenter`) restores them or removes them for good. Image files wait in a trash directory.
- **Files**: images and videos live in the images folder of `LibraryPaths` (by default `~/.moodframe/images`),
  thumbnails under `~/.moodframe/thumbnails`; the database stores their paths.
- **Settings** are a properties file `~/.moodframe/config.properties`, not part of the database (see "Folders and
  settings").

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
use in `~/.moodframe/secret.key` (`LibraryPaths.secretKey()`), readable only by the user. A copied
`config.properties` alone reveals no key, and no key is derived from constants in the code. `util.Secrets` keeps
keys and tokens out of logs and messages.

### D4: Provider clients behind interfaces, everything else concrete

`BflApiService`, `GeminiApiService`, `OpenAiApiService` and `RequestResponseService` are interfaces because tests
replace them with mocks and fake HTTP servers. Interfaces with a single implementation and no consumer typed against
them were removed; a new interface is introduced when a second implementation or a test seam needs it.

The three provider clients extend the provider SPI `service.provider.ImageProvider` (key, connection test,
submission, seeds, attached images) and keep their own extras, such as the BFL job polling or the brainstorm chat.
`MoodframeApplication` builds one `ProviderRegistry` and passes it to the services that dispatch by provider
(`RequestResponseManager`, `ApiProviderValidator`); every constant of `GenerationModel` names its `ApiProvider`. A new
provider is a client, its entry in the registry, its models, its constant in `ApiProvider` (whose prefix names its
account settings in `ProviderAccounts`) and its key in `config.Secret`.

### D5: Detailed logging as a log level

The setting "detailed logging" sets the logback logger `art.moodframe` to DEBUG and back to the configured level
(`config.DetailedLogging`), at startup and on every change. Code logs details with `logger.debug(...)` instead of
asking the setting, and guards a costly argument with `logger.isDebugEnabled()`. Only the startup, the settings
dialog and the configuration export read `getLoggingEnabled()`.
