plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop")
    
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                
                // Inclui binários para Linux e Windows no mesmo JAR
                implementation(compose.desktop.linux_x64)
                implementation(compose.desktop.windows_x64)
                
                implementation("com.google.code.gson:gson:2.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "B3Check"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
    archiveFileName.set("B3Check.jar")
}
