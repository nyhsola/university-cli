<pre align="center">
__  __     ______     ______     ______
/\ \/\ \   /\  == \   /\  __ \   /\  ___\
 \ \ \_\ \  \ \  __&lt;   \ \  __ \  \ \ \__ \
   \ \_____\  \ \_\ \_\  \ \_\ \_\  \ \_____\
    \/_____/   \/_/ /_/   \/_/\/_/   \/_____/
</pre>

<h1 align="center">University RAG CLI</h1>

<p align="center">
  Local-first RAG for indexing project documents and asking questions through Ollama.
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-JVM-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Ollama" src="https://img.shields.io/badge/Ollama-local-111111?logo=ollama&logoColor=white">
  <img alt="SQLite" src="https://img.shields.io/badge/SQLite-sqlite--vec-003B57?logo=sqlite&logoColor=white">
</p>

`urag` is a Kotlin/JVM command-line application that recursively indexes project `.txt` files, stores their vectors locally in SQLite, retrieves relevant chunks, and generates structured answers with a local Ollama model.

## Highlights

- Fully local document indexing and question answering
- Dense vector search powered by `sqlite-vec`
- Configurable embedding model and chunking strategy
- Configuration files stored as JSON resources and selected by ID
- Structured JSON responses from Ollama
- One-shot shell commands and a full-screen interactive terminal
- Persistent operation cancellation with `Ctrl+C`

## How it works

```mermaid
flowchart LR
    A[Project .txt files] --> B[Chunking]
    B --> C[Document embeddings]
    C --> D[(SQLite + sqlite-vec)]
    Q[Question] --> E[Query embedding]
    E --> D
    D --> F[Relevant chunks]
    F --> G[Ollama]
    G --> H[Structured answer]
```

## Requirements

- JDK 21
- Ollama available at `http://localhost:11434`
- Embedding model `qwen3-embedding:8b`
- Question model `qwen3.5:9b`

Pull the default models:

```shell
ollama pull qwen3-embedding:8b
ollama pull qwen3.5:9b
```

## Build

Linux and macOS:

```shell
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

The executable fat JAR is created at `build/libs/urag.jar`.

## Quick start

List the available indexing configurations and select one by its resource ID:

```shell
java -jar build/libs/urag.jar configurations
java -jar build/libs/urag.jar index 1
```

Inspect the generated indexes, then retrieve context or ask a question using an index ID from `list`:

```shell
java -jar build/libs/urag.jar list
java -jar build/libs/urag.jar relevant 1 "What is dependency injection?"
java -jar build/libs/urag.jar question 1 "Summarize this document"
```

> [!IMPORTANT]
> `/index` accepts a **resource configuration ID** shown by `configurations`. Commands `relevant` and `question` accept an **index ID** shown by `list`. An index is a stored combination of one document and one configuration hash.

## Commands

The `/` prefix is required in interactive mode and optional in one-shot shell mode.

| Command | Description |
| --- | --- |
| `configurations` | List JSON indexing configurations, their IDs, models, strategies, and parameters. |
| `index <configurationId>` | Recursively index project `.txt` files using the selected resource configuration. |
| `list` | List stored index IDs, source files, and indexing statuses. |
| `relevant <indexId> "question"` | Print the three chunks most relevant to the question. |
| `question <indexId> "question"` | Retrieve context and generate an answer with Ollama. |
| `help` | Display the available commands. |
| `exit` | Exit interactive mode. |

Running without arguments, with `-h`, or with `--help` displays command help. Failed one-shot commands return a non-zero exit code.

## Interactive mode

```shell
java -jar build/libs/urag.jar --interactive
```

Start typing `/` to open command suggestions. Available terminal controls:

| Input | Action |
| --- | --- |
| `↑` / `↓` | Navigate command suggestions, including suggestions outside the visible window. |
| `Enter` | Complete the selected suggestion or execute the current command. |
| `Esc` | Close command suggestions. |
| `Page Up` / `Page Down` | Scroll through chat history. |
| Mouse wheel | Scroll through chat history. |
| `Ctrl+C` | Cancel the active operation; press again when idle to exit. |

Text can be selected normally with the mouse, without holding `Shift`.

## Indexing configurations

Configuration files live in `src/main/resources/configurations` and are loaded at runtime. Each file has a stable positive ID:

```json
{
  "id": 1,
  "embeddingModel": "qwen3-embedding:8b",
  "strategy": "FIXED_SIZE",
  "parameters": {
    "chunkSize": "1000"
  }
}
```

IDs must be unique across configuration files. Configurations are displayed in ascending ID order, and `default.json` is marked with `*`.

The application stores only the SHA-256 hash of the selected JSON configuration in the indexing database. Changing the file—including formatting—changes its hash and requires reindexing affected documents.

## Runtime data

The current working directory is treated as the project root. Run the JAR from the directory whose documents should be indexed.

```text
.data/
├── university.db
└── chunks/
    └── chunks_{indexId}.jsonl
```

- `.data/university.db` contains documents, index records, statuses, and vectors.
- `.data/chunks/` contains the generated document chunks.
- `.data` itself is excluded from recursive indexing.

> [!NOTE]
> The baseline migration describes a fresh database. When its schema changes during development, remove or migrate an existing `.data/university.db` before running indexing again.

## Tech stack

- Kotlin/JVM and Gradle
- Koin dependency injection
- Mordant terminal UI
- Kotlinx Serialization
- Ollama structured outputs
- SQLite, Flyway, and `sqlite-vec`
