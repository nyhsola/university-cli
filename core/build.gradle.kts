plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
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
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    sqliteVecExtension("io.github.asg017:sqlite-vec:$sqliteVecVersion:$sqliteVecPlatform@tar.gz")
}

tasks.processResources {
    from({ tarTree(resources.gzip(sqliteVecExtension.singleFile)) }) {
        into("native/sqlite-vec")
    }
}
