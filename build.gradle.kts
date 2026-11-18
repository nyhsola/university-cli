import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
}

val detektConfig = layout.projectDirectory.file("config/detekt/detekt.yml")

allprojects {
    group = "university.cli"
    version = "1.0"

    repositories {
        mavenCentral()
    }
}

val modules = subprojects.filter { it.childProjects.isEmpty() }

configure(modules) {
    apply(plugin = "dev.detekt")

    extensions.configure<DetektExtension> {
        config.setFrom(detektConfig)
        buildUponDefaultConfig = true
        parallel = true
        autoCorrect = false
    }

    dependencies {
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
    }

    configurations.named("detekt") {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.4.10")
                because("Detekt 2.0.0-alpha.6 is compiled against Kotlin 2.4.10")
            }
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
        exclude("**/build/**")
    }

    tasks.register<Detekt>("detektFormat") {
        group = "formatting"
        description = "Formats Kotlin sources using the configured Detekt rules."
        setSource(files("src/main/kotlin", "src/test/kotlin"))
        config.setFrom(detektConfig)
        buildUponDefaultConfig = true
        disableDefaultRuleSets = true
        autoCorrect = true
        ignoreFailures = true
        parallel = true
        include("**/*.kt")
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.assemble {
    dependsOn(modules.map { "${it.path}:assemble" })
}

tasks.check {
    dependsOn(modules.map { "${it.path}:check" })
}

tasks.register("formatKotlin") {
    group = "formatting"
    description = "Formats Kotlin sources in all modules."
    dependsOn(modules.map { "${it.path}:detektFormat" })
}

tasks.clean {
    dependsOn(modules.map { "${it.path}:clean" })
}

tasks.register("compileKotlin") {
    dependsOn(modules.map { "${it.path}:compileKotlin" })
}

tasks.register("test") {
    dependsOn(modules.map { "${it.path}:test" })
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the CLI from the project directory."
    dependsOn(":app:jar")
    classpath = files(layout.buildDirectory.file("libs/urag.jar"))
    mainClass.set("university.cli.MainKt")
    workingDir = rootDir
    standardInput = System.`in`
}
