plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":backend-parent:core"))
    implementation(project(":backend-parent:storage-impl"))
    implementation(project(":backend-parent:llm-impl"))
}
