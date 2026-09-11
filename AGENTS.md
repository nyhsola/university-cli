# Repository Guide

## Project

`university-cli` is a single-module Kotlin/JVM 21 CLI application built with Gradle 9.6. It indexes project `.txt` files, stores vectors in SQLite through `sqlite-vec`, retrieves relevant chunks, and asks a local Ollama model for a structured JSON answer. It supports one-shot commands and a full-screen Mordant terminal UI.

The main libraries are Koin, Mordant, kotlinx.serialization, SQLite JDBC, Flyway, and `sqlite-vec`. The first resource-processing build may download the platform-specific `sqlite-vec` archive.

## Repository Layout

- `src/main/kotlin/university/cli/Main.kt`: composition entry point only; starts Koin and delegates to `AppLauncher`.
- `launcher/`: selects one-shot or `--interactive` execution.
- `di/`: the only place that should know about Koin; register new dependencies and commands in `AppModule`.
- `command/`: `ChatCommand` implementations, dispatch, ordering, and command-specific presentation.
- `config/`: process-level directory, database, and Ollama settings.
- `database/`: Flyway execution, SQLite datasource setup, and native `sqlite-vec` loading.
- `repository/`: concrete JDBC repositories. Keep SQL out of Kotlin.
- `model/`: domain and serialization types.
- `service/configuration/`: loading indexing and question JSON configurations.
- `service/indexing/`: project discovery, chunking, chunk files, and indexing orchestration.
- `service/llm/`: Ollama HTTP access and embedding-specific preprocessing.
- `service/retrieval/`: vector search, context assembly, and question answering.
- `service/chat/`, `service/cli/`: interactive and one-shot terminal front ends.
- `service/operation/`: cancellation of long-running operations.
- `util/`: focused reusable utility classes and functions; do not move workflow or domain logic here.
- `src/main/resources/configurations/{indexing,question}/`: typed JSON configurations selected by stable positive IDs.
- `src/main/resources/db/migration/`: Flyway migrations.
- `src/main/resources/db/sql/`: repository SQL grouped by aggregate.
- `src/main/resources/prompts/`: reusable static prompts.
- `src/test/kotlin/`: JUnit Platform tests using `kotlin.test`.
- `examples/`: sample text corpus, not test fixtures.

Runtime state is written under `.data/`; generated outputs also include `build/`, `.gradle/`, `.gradle-user-home/`, and `.kotlin/`. Never commit or hand-edit these directories.

## Build and Run

Use the Gradle wrapper:

```bash
./gradlew compileKotlin
./gradlew test
./gradlew check
./gradlew build
./gradlew run --args="cf-index"
./gradlew run --args="--interactive"
```

On Windows, replace `./gradlew` with `.\gradlew.bat`. `build` compiles, runs tests, processes the native extension, and creates the fat JAR at `build/libs/urag.jar`.

Run the packaged application from the project directory to index that directory:

```bash
java -jar build/libs/urag.jar help
java -jar build/libs/urag.jar cf-index
java -jar build/libs/urag.jar --interactive
```

Indexing and question commands require Ollama at the endpoint in `OllamaConfig` and the models named by the active resource configurations.

There is no configured ktlint, Detekt, or formatter task. Do not report a lint/format check that does not exist. Use `./gradlew check` and `git diff --check`.

## Architectural Rules

- Apply SOLID and object-oriented design pragmatically: keep each class focused on one responsibility, separate orchestration from persistence and presentation, and prefer focused collaborators over large multipurpose services.
- Keep `Main.kt` and `AppLauncher` thin. Commands validate arguments and render output; services own workflows; repositories own JDBC.
- Use constructor injection. Production classes must not call Koin APIs directly.
- Prefer concrete `Jdbc...Repository` classes. Add an interface only when multiple implementations or an explicit boundary requires one.
- Put every repository query in `src/main/resources/db/sql` and load it with `KClass<*>.loadResource`; do not embed SQL strings in Kotlin.
- Add schema changes as ordered Flyway migrations. Do not rewrite an already-applied migration unless the task explicitly requires a baseline change; doing so changes Flyway checksums for existing `.data/university.db` files.
- `IndexConfigurationService` and `QuestionConfigurationService` own resource discovery, ID validation, defaults, and decoding. Keep indexing and question configurations in their respective resource directories.
- The database stores an indexing configuration hash, not its model and chunking fields. Retrieval resolves the current JSON by that hash. Changing an indexing JSON file, including formatting, invalidates matching stored indexes and requires reindexing. Do not casually renumber configuration IDs.
- All document and query embeddings go through `EmbedService`. Only it should call `OllamaService.embed`, because it owns model-specific instructions. Structured generation uses `OllamaService.question<T>()` with a kotlinx-serializable result type.
- Keep `OllamaService` as the low-level HTTP/serialization boundary; do not add retrieval, prompting strategy, or UI concerns to it.
- Preserve cancellation checks in indexing/retrieval loops. When catching `InterruptedException`, restore the thread interrupt flag.
- Register commands in `AppModule` and add their display position to `COMMAND_DISPLAY_ORDER`; `/help` and interactive suggestions share this ordering.

## Kotlin and Resource Conventions

- Follow Kotlin official style: four-space indentation, `PascalCase` types, `camelCase` members, and trailing commas in multiline declarations.
- Place `companion object` at the beginning of a class, before properties and methods.
- Move stateless, reusable utility methods that do not belong to a domain object or workflow into a focused class or object in `util/`; avoid generic catch-all utility classes.
- Prefer concise positional constructor/function calls when the arguments are obvious. Use named arguments only to remove ambiguity.
- Keep terminal command names, usage strings, statuses, and errors in English.
- Use `kotlinx.serialization` and the shared `JsonUtil`; do not introduce Gson or Jackson.
- Model extensible configuration options as `Map<String, String>` and validate required values at the service that interprets them.
- Reuse `TextUtil` and the existing box-formatting helpers for terminal output instead of duplicating wrapping and width logic.
- Static reusable resource text should be loaded through `loadResource`; resolve resource paths from the classpath, not the working directory.

## Implementing Changes

1. Trace the complete path affected by the change: command → service → repository/resource → model.
2. Preserve package boundaries and inject new collaborators through constructors and `AppModule`.
3. Update related resource JSON, SQL, migration, command help/order, and README only when runtime behavior changes require it.
4. Validate configuration parameters and command arguments at their owning boundary with actionable errors.
5. Do not overwrite unrelated working-tree changes.

## Tests

The current suite contains only `AppLauncherTest`, a smoke test that verifies `AppLauncher.launch()` completes without external services. Keep that test narrow and independent of Ollama and persistent `.data` state.

Do not add or expand tests by default unless the task explicitly requests tests. When tests are requested or an existing test must change:

- use `kotlin.test` on JUnit Platform;
- name files `<Subject>Test.kt` and mirror the production package;
- prefer deterministic unit tests for parsing, validation, ranking, and formatting;
- use temporary directories/databases for filesystem or JDBC tests;
- do not require a running Ollama instance in the normal test task.

## Do Not Change Without a Good Reason

- Gradle wrapper files, dependency versions, fat-JAR packaging, or platform mapping for `sqlite-vec`.
- Native extension loading and SQLite foreign-key/load-extension settings.
- Existing migration history or the persisted database/chunk-file formats.
- Default configuration IDs, models, or parameters: these affect CLI examples and existing index hashes.
- Terminal escape sequences, alternate-screen handling, cursor behavior, scrolling, and command ordering when the task is unrelated to the UI.
- Files in `examples/`, `README.md`, or `INVESTIGATE.md` unless the requested behavior or documentation scope calls for it.

## Completion Checklist

- Review the diff for accidental changes and run `git diff --check`.
- Run the narrowest relevant verification while iterating, then `./gradlew build` for completed code, dependency, SQL, migration, or resource changes.
- For test-only changes, run `./gradlew test`; for documentation-only changes, build is unnecessary.
- Smoke-test affected one-shot commands from `build/libs/urag.jar` when command parsing or visible output changes.
- Verify interactive behavior in a real terminal when changing raw input, scrolling, cursor, escape sequences, or screen layout.
- State any checks that could not be run, especially Ollama-dependent indexing or question flows.
