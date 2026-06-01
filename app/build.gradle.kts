import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // --- ИСПОЛЬЗУЕМ ПСЕВДОНИМЫ ДЛЯ ВСЕХ ПЛАГИНОВ ---
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val neuroServerUrl = localProperties.getProperty("neuro.serverUrl", "http://10.0.2.2:3510")

android {
    namespace = "com.kazancev.ai_chat_companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kazancev.ai_chat_companion"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "NEURO_SERVER_URL", "\"$neuroServerUrl\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // --- БАЗОВЫЕ ЗАВИСИМОСТИ ANDROIDX ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // --- JETPACK COMPOSE (UI) ---
    val composeBomVersion = "2024.05.00"
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // --- НАВИГАЦИЯ И VIEWMODEL ДЛЯ COMPOSE ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- СЕТЕВЫЕ ЗАПРОСЫ (KTOR) ---
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // --- СЕРИАЛИЗАЦИЯ (KOTLINX.SERIALIZATION) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // --- ЛОКАЛЬНОЕ ХРАНИЛИЩЕ (DATASTORE) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- ОТОБРАЖЕНИЕ MARKDOWN (COMPOSE RICHTEXT) ---
    val richTextVersion = "0.16.0"
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:$richTextVersion")
    implementation("com.halilibo.compose-richtext:richtext-commonmark:$richTextVersion")

    // --- ТЕСТИРОВАНИЕ ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
