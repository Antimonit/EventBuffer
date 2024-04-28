plugins {
    id("me.khol.gradle.android.library")
    id("me.khol.gradle.publish.android")
}

android {
    namespace = "me.khol.arch"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.runtime)

    testImplementation(projects.internal.test)
    testImplementation(libs.junit4)
    testImplementation(libs.strikt)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.strikt)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.lifecycle.livedata)
    androidTestImplementation(libs.androidx.fragment.testing)
}
