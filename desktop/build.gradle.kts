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
            packageName = "com.mrafshun20.lasernesting"
            packageVersion = "2.0.0"
            description = "ibebels — Bystronic sheet nesting (IGEMS-style)"
            vendor = "Mrafshun20"
            iconFile = project.file("src/main/resources/icon.png")
            windows {
                menu = true
                menuGroup = "ibebels"
                shortcut = true
            }
        }
    }
}
