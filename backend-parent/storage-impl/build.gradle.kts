plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":backend-parent:core"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.flywaydb:flyway-core:13.4.0")
}
