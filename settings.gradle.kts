plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "university-cli"
include(
    "app",
    "backend-parent:cli-impl",
    "backend-parent:core",
    "backend-parent:storage-impl",
    "backend-parent:llm-impl",
    "backend-parent:index-impl",
    "backend-parent:query-impl",
    "backend-parent:judge-impl",
)
