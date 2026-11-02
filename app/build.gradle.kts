plugins {
    id("com.android.application")
}

android {
    namespace = "com.wgh.splashcleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wgh.splashcleaner"
        minSdk = 23
        targetSdk = 36
        versionCode = 30102
        versionName = "3.1.2-enhance"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
