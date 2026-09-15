plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.flux.hourglass"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.flux.hourglass"
    minSdk = 24
    targetSdk = 36
    val resolvedVersionName = (findProperty("VERSION_NAME") as String?) ?: "1.10.0"
    // Derived from versionName so it never needs a manual bump; keep this
    // formula in sync with scripts/export-play-store-release.ps1. Parsing
    // is lazy (inside the ?: branch) so an explicit VERSION_CODE override
    // still works even when versionName isn't in strict X.Y.Z form.
    versionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull()
      ?: run {
        val parts = resolvedVersionName.split(".")
        // 1-4 digits, no sign — matches ^\d{1,4}$ in
        // scripts/export-play-store-release.ps1's Resolve-VersionCode.
        // Capping each segment's length (not just checking it parses)
        // keeps the multiplication below Long's range by a huge margin,
        // so it can't silently wrap the way unbounded Int/Long math did.
        require(parts.size == 3 && parts.all { it.isNotEmpty() && it.length <= 4 && it.all(Char::isDigit) }) {
          "VERSION_NAME '$resolvedVersionName' must be in X.Y.Z numeric form (each part 0-9999) to derive a versionCode"
        }
        val (verMajor, verMinor, verPatch) = parts.map { it.toLong() }
        // Long arithmetic so an oversized segment (e.g. a typo'd major)
        // can't silently wrap Int32 into a smaller-but-valid versionCode;
        // Play Console's own documented ceiling is 2_100_000_000.
        val derived = verMajor * 1_000_000 + verMinor * 1_000 + verPatch
        require(derived in 1..2_100_000_000L) {
          "Derived versionCode $derived for VERSION_NAME '$resolvedVersionName' is outside Play Console's valid range (1..2,100,000,000)"
        }
        derived.toInt()
      }
    versionName = resolvedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: System.getenv("RELEASE_STORE_FILE")
        ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugKeystore = file("${rootDir}/debug.keystore")
      if (debugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
