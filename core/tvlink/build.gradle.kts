plugins {
    id("studyflow.android.library")
}

android {
    namespace = "com.mikeyphw.studyflow.core.tvlink"
}

dependencies {
    api(project(":core:tvcontrol"))
    testImplementation(libs.junit4)
}
