import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Cloud keys live in the gitignored root supabase.properties (see supabase.properties.example); blank = cloud not set up.
val cloudProps = Properties().apply {
    rootProject.file("supabase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun cloudField(key: String): String =
    "\"" + (cloudProps.getProperty(key) ?: "").trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""


android {
    namespace = "com.local.salarytracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.local.salarytracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
        buildConfigField("String", "SUPABASE_URL", cloudField("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", cloudField("SUPABASE_ANON_KEY"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", cloudField("GOOGLE_WEB_CLIENT_ID"))
    }

    signingConfigs {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            val props = Properties().apply { propsFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the real app (different package, own data); name comes from src/debug/res.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}
