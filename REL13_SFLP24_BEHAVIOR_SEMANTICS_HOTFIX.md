# REL13 SFLP24 behavior semantics hotfix v3

This overlay rebases the REL13 behavior/classpath fixes on the fresh StudyFlow snapshot and repairs a false-positive SFLP22/SFLP23 release verifier.

The behavior fixes remain unchanged from v2: Android/app-TV receive `kotlin("test-junit")`, Android phone profiles advertise delegated keyboard input while Desktop composition is deduplicated, and presentation projection rejects forbidden write capabilities before generic missing capabilities.

REL13 v2 proved these semantics through the SFLP10-SFLP21 gates, but `verifySflp22Sflp23AccountBackupValidation` then failed because its cycle guard searched the entire `build.gradle.kts`. The normal ordered validation-task list contains SFLP20/SFLP21 immediately before SFLP22/SFLP23, which matched that global substring even though the actual SFLP20/SFLP21 Gradle task does not depend on SFLP22/SFLP23.

v3 scopes the cycle check to the `verifySflp20Sflp21ShareImportConflict` task definition only. It does not remove or weaken the cycle rejection itself.
