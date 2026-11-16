# Repository Guide

## Project

`university-cli` is a multi-module Kotlin/JVM 21 CLI application built with Gradle 9.6. It indexes project `.txt` files, stores vectors in SQLite through `sqlite-vec`, retrieves relevant chunks, and asks a local Ollama model for a structured JSON answer. It supports one-shot commands and a full-screen Mordant terminal UI.

The main libraries are Koin, Mordant, kotlinx.serialization, SQLite JDBC, Flyway, and `sqlite-vec`. The first resource-processing build may download the platform-specific `sqlite-vec` archive.

## Repository Layout

The root project coordinates builds and runs. Kotlin packages remain under `university.cli`; module directories define compilation boundaries.

- `core/`: shared domain models, profiles, operation cancellation/logging, directory settings, JSON/resource/file helpers, and all application resources. It has no dependencies on other project modules.
- `storage-sqlite/`: concrete JDBC repositories, datasource setup, Flyway, and native `sqlite-vec` loading. SQL and migrations live in `core/src/main/resources/db/{sql,migration}/`.
- `llm-ollama/`: Ollama HTTP/serialization, embedding preprocessing, Ollama settings, JSON schema generation, and loading embedding instructions from core resources.
- `rag/`: project discovery, chunking, indexing/removal, retrieval, context assembly, and question answering. Profile loaders live in `service/configuration/`; profile JSON lives in `core/src/main/resources/configurations/{indexing,query}/`.
- `evaluation/`: evaluation datasets/models, LLM judging, metrics, profile comparisons, report orchestration/writing, and loading the judge prompt from core resources.
- `cli/`: `Main.kt`, `launcher/`, `di/AppModule.kt`, commands, one-shot and interactive terminal front ends, and terminal formatting/parsing helpers. The launcher smoke test lives in `cli/src/test/kotlin/`.
- `@data/examples/`: sample text corpus, not test fixtures.
- `@data/research/`: research notes, experiment evidence, and development plans.

Each module uses the standard `src/main/kotlin` directory. Store all application resources centrally in `core/src/main/resources`, including SQL, migrations, profile JSON, and prompts. Do not add resource directories to other modules. The `core` resource-processing task packages the platform-specific sqlite-vec library under `native/sqlite-vec`. Preserve classpath paths and configuration bytes when moving resources; the consuming services remain in their respective modules.

Dependencies flow from `storage-sqlite` and `llm-ollama` to `core`; `rag` uses those three modules; `evaluation` uses `rag`, `storage-sqlite`, `llm-ollama`, and `core`; `cli` assembles all modules. Koin and Mordant belong only to `cli`. Keep concrete repository dependencies for now; introduce interfaces only for an explicit architectural need. Avoid circular dependencies and a catch-all utilities module.

Runtime state is written under `.data/`; generated outputs also include `build/`, `.gradle/`, `.gradle-user-home/`, and `.kotlin/`. Never commit or hand-edit these directories.

## Build and Run

Use the Gradle wrapper:

```bash
./gradlew compileKotlin
./gradlew test
./gradlew check
./gradlew build
./gradlew run --args="profiles"
./gradlew run --args="--interactive"
```

Root tasks aggregate module compilation, tests, and checks. `run` launches from the root directory; module tasks can also be addressed directly, such as `:rag:compileKotlin` or `:cli:test`.

On Windows, replace `./gradlew` with `.\gradlew.bat`. `build` compiles, runs tests, processes the native extension, and creates the fat JAR at `build/libs/urag.jar`.

Run the packaged application from the project directory to index that directory:

```bash
java -jar build/libs/urag.jar help
java -jar build/libs/urag.jar profiles
java -jar build/libs/urag.jar --interactive
```

Indexing and question commands require Ollama at the endpoint in `OllamaConfig` and the models named by the active resource configurations.

There is no configured ktlint, Detekt, or formatter task. Do not report a lint/format check that does not exist. Use `./gradlew check` and `git diff --check`.

## Architectural Rules

- Apply SOLID, KISS, DRY, YAGNI, BDUF (Big Design Up Front), and APO (Avoid Premature Optimization) pragmatically: plan the design before implementation, keep solutions simple, avoid duplication and speculative features, and optimize only when justified. Keep each class focused on one responsibility, separate orchestration from persistence and presentation, and prefer focused collaborators over large multipurpose services.
- Follow the Boy Scout Rule: whenever you modify a class, leave it cleaner than you found it through focused improvements within the task's scope.
- Write readable, maintainable, self-documenting code with clear names and straightforward structure. Do not add code comments; express intent through the code itself.
- Keep `Main.kt` and `AppLauncher` thin. Commands validate arguments and render output; services own workflows; repositories own JDBC.
- Use constructor injection. Production classes must not call Koin APIs directly.
- Prefer concrete `Jdbc...Repository` classes. Add an interface only when multiple implementations or an explicit boundary requires one.
- Put every repository query in `core/src/main/resources/db/sql` and load it with `KClass<*>.loadResource`; do not embed SQL strings in Kotlin.
- Add schema changes as ordered Flyway migrations. Do not rewrite an already-applied migration unless the task explicitly requires a baseline change; doing so changes Flyway checksums for existing `.data/university.db` files.
- `IndexProfileService` and `QueryProfileService` own resource discovery, ID validation, defaults, and decoding. Keep index and query profiles in their respective resource directories in `core`.
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

- Do not add or write tests unless the user explicitly requests them. Existing checks may still be run to validate changes.

1. Trace the complete path affected by the change: command → service → repository/resource → model.
2. Preserve package boundaries and inject new collaborators through constructors and `AppModule`.
3. Update related resource JSON, SQL, migration, command help/order, and README only when runtime behavior changes require it.
4. Validate configuration parameters and command arguments at their owning boundary with actionable errors.
5. Do not overwrite unrelated working-tree changes.
