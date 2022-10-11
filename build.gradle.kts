buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    }
}

plugins {
    java
    kotlin("jvm") version "1.6.0"
    `maven-publish`
}

tasks {
    registering(Delete::class) {
        delete(buildDir)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.skat"
            artifactId = "ticketauth-android"
            version = "1.0.0"

            from(components["java"])
        }
    }
}
