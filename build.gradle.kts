plugins {
    // Применяем плагины БЕЗ версий и БЕЗ apply
    // Версии берутся из settings.gradle.kts автоматически
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}