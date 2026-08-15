plugins {
    id("studyflow.android.library")
}

android {
    namespace = "com.mikeyphw.studyflow.core.data"
}

dependencies {
    implementation(project(":core:diagnostics"))
    implementation(project(":core:database"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:portability"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
}


// Validation compatibility: core:data is linted as an isolated Android
// library, so desugaring must be visible directly to this module.
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}


dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
