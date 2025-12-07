package me.khol.gradle.pure

import me.khol.gradle.Const
import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("me.khol.gradle.kover.pure")
}

java {
    sourceCompatibility = Const.javaVersion
    targetCompatibility = Const.javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Const.javaVersion.toString()))
    }
}
