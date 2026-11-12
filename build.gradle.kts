plugins {
    base
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
}

allprojects {
    group = "university.cli"
    version = "1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
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
    dependsOn(subprojects.map { "${it.path}:assemble" })
}

tasks.check {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.clean {
    dependsOn(subprojects.map { "${it.path}:clean" })
}

tasks.register("compileKotlin") {
    dependsOn(subprojects.map { "${it.path}:compileKotlin" })
}

tasks.register("test") {
    dependsOn(subprojects.map { "${it.path}:test" })
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the CLI from the project directory."
    dependsOn(":cli:jar")
    classpath = files(layout.buildDirectory.file("libs/urag.jar"))
    mainClass.set("university.cli.MainKt")
    workingDir = rootDir
    standardInput = System.`in`
}
