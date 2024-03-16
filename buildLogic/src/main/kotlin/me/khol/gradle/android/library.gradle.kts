package me.khol.gradle.android

import me.khol.gradle.Const

plugins {
    id("com.android.library")
    kotlin("android")
    id("me.khol.gradle.kover.android")
}

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = Const.javaVersion
        targetCompatibility = Const.javaVersion
    }

    kotlinOptions {
        jvmTarget = Const.javaVersion.toString()
    }
}
