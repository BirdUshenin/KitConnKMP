import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "com.kitconn.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "KitConn VPN"
            packageVersion = "4.0.0"
            description = "KitConn VPN"
            vendor = "KitConn"

            // Ядро Xray лежит по папкам ОС: appResources/windows-x64/xray.exe, appResources/macos-arm64/xray
            appResourcesRootDir.set(layout.projectDirectory.dir("appResources"))

            // jlink собирает урезанную JVM: перечисляем модули, нужные сети и TLS (без jdk.crypto.ec ломаются HTTPS-соединения)
            modules("java.net.http", "java.naming", "jdk.unsupported", "java.management", "jdk.crypto.ec")

            windows {
                menuGroup = "KitConn"
                shortcut = true
                // Установка для текущего пользователя: не нужны права администратора
                perUserInstall = true
                // Постоянный идентификатор: по нему MSI понимает, что это обновление, а не новое приложение
                upgradeUuid = "6f1e2c1a-7b5d-4a63-9d5e-2d7a8f4b9c10"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
