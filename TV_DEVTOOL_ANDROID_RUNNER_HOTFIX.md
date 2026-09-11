# StudyFlow TV Devtool Android runner hotfix

The `studyflow_tv` target previously declared `type = "gradle"`. On native Termux this made `devtool build --target studyflow_tv` invoke the raw Termux Gradle launcher, which can fail before task execution when Gradle's Linux `libnative-platform.so` cannot load under Android/Termux.

The target is now declared as `type = "android"`, matching the mobile StudyFlow target. Devtool therefore routes TV builds through its Android build wrapper while retaining the existing `:app-tv` task lists.

The TV Android runner contract is explicit:

- debug variant;
- Termux AAPT2;
- native-Termux preference;
- NDK `29.0.14206865`;
- `:app-tv:assembleDebug` as the TV APK task;
- no accidental `:app:assembleDebug` in the TV build target.

`verifyStudyFlowTvDevtoolAndroidRunner` is included in the TV build and validation preflight so the target cannot silently regress to the generic Gradle runner.

Expected standalone build:

```bash
cd "$HOME/Code/studyflow"
gradle --stop || true
devtool --yes -r "$HOME/Code/studyflow" --target studyflow_tv build
```
