plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage-sqlite"))
    implementation(project(":llm-ollama"))
    implementation(project(":rag"))
    implementation(project(":evaluation"))
    implementation(platform("io.insert-koin:koin-bom:4.2.0"))
    implementation("io.insert-koin:koin-core")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("university.cli.MainKt")
}

tasks.run {
    workingDir = rootProject.projectDir
}

val copyExampleBooks = tasks.register<Sync>("copyExampleBooks") {
    from(rootProject.layout.projectDirectory.dir("@data/examples/books"))
    into(rootProject.layout.buildDirectory.dir("libs/books"))
}

val copyExampleDatasets = tasks.register<Sync>("copyExampleDatasets") {
    from(rootProject.layout.projectDirectory.dir("@data/examples/dataset"))
    into(rootProject.layout.buildDirectory.dir("libs/dataset"))
}

val copyAgentDatasets = tasks.register<Sync>("copyAgentDatasets") {
    from(rootProject.layout.projectDirectory.dir("@data/examples/dataset-agent"))
    into(rootProject.layout.buildDirectory.dir("libs/dataset-agent"))
}

tasks.jar {
    archiveFileName.set("urag.jar")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
    manifest {
        attributes["Main-Class"] = "university.cli.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val runtimeClasspath = configurations.runtimeClasspath
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    dependsOn(copyExampleBooks, copyExampleDatasets, copyAgentDatasets)
}
