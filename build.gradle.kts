plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    application
}

group = "university.cli"
version = "1.0"

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

application {
    mainClass.set("university.cli.MainKt")
}

tasks.jar {
    archiveFileName.set("urag.jar")
    manifest {
        attributes["Main-Class"] = "university.cli.MainKt"
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )
}

dependencies {
    implementation(platform("io.insert-koin:koin-bom:4.2.0"))
    implementation("io.insert-koin:koin-core")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.flywaydb:flyway-core:13.4.0")

    sqliteVecExtension("io.github.asg017:sqlite-vec:$sqliteVecVersion:$sqliteVecPlatform@tar.gz")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from({ tarTree(resources.gzip(sqliteVecExtension.singleFile)) }) {
        into("native/sqlite-vec")
    }
}
