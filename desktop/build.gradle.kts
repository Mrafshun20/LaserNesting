import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    // Shared pure-Kotlin core: models, nesting engine, DXF parser, export.
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
}

compose.desktop {
    application {
        mainClass = "com.mrafshun20.lasernesting.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "iBebels"
            packageVersion = "2.1.0"
            description = "iBebels - laser sheet nesting for Bystronic"
            vendor = "Mrafshun20"
            windows {
                menu = true
                menuGroup = "iBebels"
                shortcut = true
                iconFile.set(project.file("src/main/resources/ibebels.ico"))
            }
        }
    }
}
