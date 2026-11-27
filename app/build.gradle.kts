import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val neteaseLyricsBaseUrl = providers.gradleProperty("neteaseLyricsBaseUrl").orElse("").map { value ->
    val url = value.trim()
    if (url.isEmpty()) {
        ""
    } else {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "neteaseLyricsBaseUrl must be an HTTPS base URL without credentials, a query, or a fragment."
        }
        uri.toASCIIString().trimEnd('/') + "/"
    }
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")

android {
    namespace = "io.github.seven317.openstandby"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.seven317.openstandby"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "1.0.3"
        buildConfigField("String", "NETEASE_LYRICS_BASE_URL", "\"${neteaseLyricsBaseUrl.get()}\"")
    }

    if (releaseKeystorePath.isPresent) {
        val releaseSigning = signingConfigs.create("release") {
            storeFile = file(releaseKeystorePath.get())
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = releaseSigning
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)
    implementation(libs.coil.android)
    implementation(libs.coil.network.okhttp)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.jsoup)
    implementation(libs.opencc4j)
    testImplementation(libs.junit)
}
