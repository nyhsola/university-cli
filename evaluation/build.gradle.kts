plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":core"))
    implementation(project(":rag"))
    implementation(project(":storage-sqlite"))
    implementation(project(":llm-ollama"))
}
