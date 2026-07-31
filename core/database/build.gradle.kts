plugins {
    id("studyflow.android.library.room")
}

android {
    namespace = "com.mikeyphw.studyflow.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:portability"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    add("ksp", libs.androidx.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
