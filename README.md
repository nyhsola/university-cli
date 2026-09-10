# University RAG CLI

University RAG (`urag`) is a local Kotlin/JVM application for indexing project text files and querying them through Ollama. It supports both one-shot shell commands and a full-screen interactive chat.

## Requirements

- JDK 21
- Ollama running at `http://localhost:11434`
- Ollama models `qwen3-embedding:8b` and `qwen3.5:9b`

```shell
ollama pull qwen3-embedding:8b
ollama pull qwen3.5:9b
```

## Build

```shell
./gradlew build
java -jar build/libs/urag.jar --help
```

On Windows, use `gradlew.bat build`. The generated executable JAR is `build/libs/urag.jar`.

## Command-line mode

Run one command and return to the shell:

```shell
java -jar build/libs/urag.jar index
java -jar build/libs/urag.jar list
java -jar build/libs/urag.jar relevant 2 "What is dependency injection?"
java -jar build/libs/urag.jar question 2 "Summarize this document"
```

Commands may optionally retain their interactive `/` prefix, for example `urag.jar /list`. Running without arguments, with `--help`, or with `-h` prints command help. Invalid commands and failed operations return a non-zero exit code.

| Command | Description |
| --- | --- |
| `index` | Recursively index project `.txt` files, excluding `.data`. |
| `list` | List indexed files, configuration IDs, and statuses. |
| `relevant <id> "question"` | Print the three most relevant chunks from a configuration. |
| `question <id> "question"` | Retrieve context and ask the configured Ollama model. |
| `help` | Display available commands. |

## Interactive mode

```shell
java -jar build/libs/urag.jar --interactive
```

Interactive commands use the `/` prefix: `/index`, `/list`, `/relevant`, `/question`, `/help`, and `/exit`. Type `/` to open suggestions. Use the arrow keys to select a command, the mouse wheel or Page Up/Page Down to scroll, and Ctrl+C to cancel an operation or leave the application.

## Runtime data

The working directory is treated as the project root. The database is stored in `.data/university.db`, while generated chunks are written to `.data/chunks/chunks_{configurationId}.jsonl`. Run the JAR from the directory whose `.txt` files should be indexed.
