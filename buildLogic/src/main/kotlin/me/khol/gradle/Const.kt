package me.khol.gradle

import org.gradle.api.JavaVersion

internal object Const {

    /**
     * The JVM version to compile bytecode into.
     *
     * Don't confuse this with the JDK required to run Gradle, AGP and other plugins. The runtime
     * used by compilation tasks is different from runtime used by Android devices.
     */
    val javaVersion = JavaVersion.VERSION_1_8
}
