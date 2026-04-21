import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

val appVersionCode = providers.gradleProperty("secretaria.app.versionCode").orNull?.toIntOrNull() ?: 1
val appVersionName = providers.gradleProperty("secretaria.app.versionName").orNull ?: "1.0"
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val debugAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val debugBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"
val releaseAdMobAppId =
    providers.gradleProperty("secretaria.admobAppId").orNull
        ?: providers.environmentVariable("SECRETARIA_ADMOB_APP_ID").orNull
        ?: localProperties.getProperty("secretaria.admobAppId")
        ?: ""
val releaseBannerAdUnitId =
    providers.gradleProperty("secretaria.admobBannerAdUnitId").orNull
        ?: providers.environmentVariable("SECRETARIA_ADMOB_BANNER_AD_UNIT_ID").orNull
        ?: localProperties.getProperty("secretaria.admobBannerAdUnitId")
        ?: ""

android {
    namespace = "com.chemecador.secretaria"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chemecador.secretaria"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["admobAppId"] = releaseAdMobAppId
        manifestPlaceholders["admobBannerAdUnitId"] = releaseBannerAdUnitId
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = debugAdMobAppId
            manifestPlaceholders["admobBannerAdUnitId"] = debugBannerAdUnitId
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId
            manifestPlaceholders["admobBannerAdUnitId"] = releaseBannerAdUnitId
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    debugImplementation(libs.compose.uiTooling)
}
