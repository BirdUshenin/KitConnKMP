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

// ───────── Портативная сборка для Windows (собирается и на macOS) ─────────
// Compose кладёт в classpath нативную библиотеку Skia только для текущей ОС. Для Windows-архива заменяем её на windows-x64.
tasks.register<Sync>("stageWindowsLib") {
    group = "distribution"
    description = "Собирает lib/ для Windows: JAR приложения и зависимостей + Skia для windows-x64"
    dependsOn(tasks.named("jar"))
    into(layout.buildDirectory.dir("portable/KitConn/lib"))
    from(tasks.named("jar"))

    val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
    // Одинаковые имена файлов бывают у разных групп (androidx и org.jetbrains.androidx): добавляем группу к имени
    artifacts.filter { !it.name.startsWith("skiko-awt-runtime-") }.forEach { a ->
        from(a.file) { rename { "${a.moduleVersion.id.group}-$it" } }
    }
    val skikoVersion = artifacts.first { it.name.startsWith("skiko-awt-runtime-") }.moduleVersion.id.version
    from(
        configurations.detachedConfiguration(
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$skikoVersion"),
        ).apply { isTransitive = false },
    )
}
