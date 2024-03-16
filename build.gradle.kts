plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kover) apply false
    id("me.khol.gradle.kover.root")
}

dependencies {
    kover(project(":event-buffer-core"))
    kover(project(":event-buffer-test"))
}