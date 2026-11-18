plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":backend-parent:core"))
    implementation(project(":backend-parent:query-impl"))
    implementation(project(":backend-parent:index-impl"))
    implementation(project(":backend-parent:storage-impl"))
    implementation(project(":backend-parent:llm-impl"))
}
