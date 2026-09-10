# Repository Guidelines

## Project Structure & Module Organization

This Kotlin/JVM CLI uses Gradle and JDK 21. Production code lives in `src/main/kotlin/university/cli`. Keep `Main.kt` as the composition root, Koin wiring in `di/`, commands in `command/`, JDBC persistence in `repository/`, database infrastructure in `database/`, domain types in `model/`, and helpers in `util/`. Group workflows under `service/`: one-shot shell execution in `cli/`, terminal interaction in `chat/`, document chunking and indexing in `indexing/`, model clients in `llm/`, semantic search and RAG in `retrieval/`, and operation lifecycle concerns in `operation/`. In `config/`, keep paths in `DirectoryConfig`, database settings in `DatabaseConfig`, and Ollama settings in `OllamaConfig`. Flyway migrations belong in `src/main/resources/db/migration`. Runtime `.data/` and generated `build/` or `.gradle-user-home/` directories must not be committed.

## Architecture & Runtime Behavior

Keep `Main.kt` thin and delegate startup to `AppLauncher`. Follow SOLID pragmatically, especially Single Responsibility: each class or service should own one cohesive responsibility and have one reason to change. Separate orchestration, JDBC access, filesystem writing, and serialization. Prefer constructor injection and keep services independent of Koin APIs. Use concrete `Jdbc...Repository` classes by default; add repository interfaces only for an explicit requirement or multiple implementations. CLI actions follow the command pattern through `ChatCommand` and `CommandDispatcher`. `/index` initializes the database and recursively indexes `.txt` files except `.data`. Chunks are written to `.data/chunks/chunks_{configurationId}.jsonl`.

## Build, Test, and Development Commands

- `./gradlew build` (`.\gradlew.bat build` on Windows): compile, test, and build the fat JAR.
- `./gradlew test`: run configured JUnit Platform tests.
- `./gradlew run`: start the CLI from the project directory.
- `java -jar build/libs/urag.jar <command> [args]`: run one CLI command.
- `java -jar build/libs/urag.jar --interactive`: start the full-screen chat.
- `git diff --check`: detect whitespace errors.

## Coding Style & Naming Conventions

Use four-space indentation, `PascalCase` types, `camelCase` members, lowercase packages, and trailing commas in multiline declarations. Place `companion object` near the beginning of a class, immediately after its properties and before methods. Prefer concise positional calls on one line when arguments are clear: `Service(props, service1)`, not `Service(properties = props, service = service1)`. Use named arguments only when they prevent ambiguity. Use `kotlinx.serialization` for JSON; do not add Gson or Jackson unless explicitly required. Keep terminal text and commands in English.

## Testing Guidelines

Do not create, generate, or expand tests by default. Add or modify tests only when explicitly requested. The retained smoke test should only verify that `AppLauncher.launch()` completes without errors. Requested tests use `kotlin.test` on JUnit Platform and follow `<Subject>Test.kt` naming.

## Commit & Pull Request Guidelines

History has no stable convention. Prefer short imperative subjects such as `Add Ollama client`. Pull requests should summarize behavior changes, list verification commands, link issues, and include terminal output for visible CLI changes.
