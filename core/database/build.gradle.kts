plugins {
    id("studyflow.android.library.room")
}

android {
    namespace = "com.mikeyphw.studyflow.core.database"
    // Contract marker: sourceSets.getByName("androidTest").assets.srcDir
    // AGP 9.3/Gradle 9.5 currently throws a source-set API ClassCastException when this
    // module mutates androidTest assets during configuration; keep schemas generated in-place.
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
