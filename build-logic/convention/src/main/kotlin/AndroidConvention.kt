import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

internal const val STUDYFLOW_NDK_VERSION = "29.0.14206865"

internal fun ApplicationExtension.configureStudyFlowApplication() {
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = STUDYFLOW_NDK_VERSION

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

internal fun LibraryExtension.configureStudyFlowLibrary() {
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = STUDYFLOW_NDK_VERSION

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    lint {
        abortOnError = true
    }
}
