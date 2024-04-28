plugins {
    id("me.khol.gradle.android.library")
    id("me.khol.gradle.publish.android")
}

android {
    namespace = "me.khol.arch"
}

dependencies {
    api(projects.eventBufferCore)
    api(libs.kotlinx.coroutines.android)

    testImplementation(projects.internal.test)
    testImplementation(libs.junit4)
    testImplementation(libs.strikt)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
