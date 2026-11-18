plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    implementation(project(":backend-parent:core"))
    implementation(project(":backend-parent:storage-impl"))
    implementation(project(":backend-parent:index-impl"))
    implementation(project(":backend-parent:query-impl"))
    implementation(project(":backend-parent:judge-impl"))
    api("com.github.ajalt.mordant:mordant:3.0.2")
    testImplementation(kotlin("test"))
}
