plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "university-cli"
include("core", "storage-sqlite", "llm-ollama", "rag", "evaluation", "cli")
