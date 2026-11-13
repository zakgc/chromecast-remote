plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "uk.co.home.chromecastremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "uk.co.home.chromecastremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.3.3-prototype"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("maven.repo.local", rootProject.file(".tools/maven").absolutePath)
            it.systemProperty("user.home", rootProject.file(".tools/test-home").absolutePath)
            it.doFirst { rootProject.file(".tools/test-home").mkdirs() }
        }
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    lint { warningsAsErrors = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
}
