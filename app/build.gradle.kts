plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()

    ivy {
        name = "sqliteVecReleases"
        url = uri("https://github.com/asg017/sqlite-vec/releases/download")
        patternLayout {
            artifact("v[revision]/sqlite-vec-[revision]-loadable-[classifier].[ext]")
        }
        metadataSources {
            artifact()
        }
        content {
            includeModule("io.github.asg017", "sqlite-vec")
        }
    }
}

val sqliteVecVersion = "0.1.9"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val sqliteVecPlatform = when {
    osName.contains("win") && osArch in setOf("amd64", "x86_64") -> "windows-x86_64"
    osName.contains("linux") && osArch in setOf("amd64", "x86_64") -> "linux-x86_64"
    osName.contains("linux") && osArch in setOf("aarch64", "arm64") -> "linux-aarch64"
    osName.contains("mac") && osArch in setOf("amd64", "x86_64") -> "macos-x86_64"
    osName.contains("mac") && osArch in setOf("aarch64", "arm64") -> "macos-aarch64"
    else -> error("sqlite-vec does not provide a binary for $osName/$osArch")
}
val sqliteVecExtension = configurations.create("sqliteVecExtension")

dependencies {
    implementation(project(":backend-parent:cli-impl"))
    implementation(project(":backend-parent:core"))
    implementation(project(":backend-parent:storage-impl"))
    implementation(project(":backend-parent:llm-impl"))
    implementation(project(":backend-parent:index-impl"))
    implementation(project(":backend-parent:query-impl"))
    implementation(project(":backend-parent:judge-impl"))
    implementation(platform("io.insert-koin:koin-bom:4.2.0"))
    implementation("io.insert-koin:koin-core")
    sqliteVecExtension("io.github.asg017:sqlite-vec:$sqliteVecVersion:$sqliteVecPlatform@tar.gz")
}

application {
    mainClass.set("university.cli.MainKt")
}

tasks.run {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

tasks.processResources {
    from({ tarTree(resources.gzip(sqliteVecExtension.singleFile)) }) {
        into("native/sqlite-vec")
    }
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
