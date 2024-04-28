plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.android)
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kover)
    implementation(libs.plugin.maven.publish)
}
