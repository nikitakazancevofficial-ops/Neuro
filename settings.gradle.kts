pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // --- ВОТ ГЛАВНОЕ ИЗМЕНЕНИЕ ---
        // Добавляем репозиторий JitPack, где лежит библиотека RichText
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Neuro"
include(":app")
