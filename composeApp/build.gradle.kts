import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val androidCompileSdk = 36
val androidMinSdk = 26
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val desktopFirebaseApiKey =
    providers.gradleProperty("secretaria.firebaseApiKey").orNull
        ?: providers.environmentVariable("SECRETARIA_FIREBASE_API_KEY").orNull
        ?: localProperties.getProperty("secretaria.firebaseApiKey")

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    android {
        namespace = "com.chemecador.secretaria.shared"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidMain.dependencies {
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.kotlinx.coroutinesPlayServices)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.chemecador.secretaria.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.chemecador.secretaria"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run" && !desktopFirebaseApiKey.isNullOrBlank()) {
        jvmArgs("-Dsecretaria.firebaseApiKey=$desktopFirebaseApiKey")
    }
}
