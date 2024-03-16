plugins {
    id("me.khol.gradle.android.library")
}

android {
    namespace = "me.khol.arch"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.lifecycle.common)
    implementation(libs.lifecycle.runtime)

    testImplementation(projects.internal.test)
    testImplementation(libs.junit4)
    testImplementation(libs.strikt)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.arch.core.testing)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.strikt)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.lifecycle.livedata)
    androidTestImplementation(libs.fragment.testing)
}
