package me.khol.gradle.pure

import me.khol.gradle.Const
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = Const.javaVersion
    targetCompatibility = Const.javaVersion
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = Const.javaVersion.toString()
    }
}
