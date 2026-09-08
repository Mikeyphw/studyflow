# TVOV19 manifest/packaging/Devtool hardening contracts

Acceptance requires:

- required `android.software.leanback` plus optional touchscreen;
- Leanback-only launcher activity and stable `com.mikeyphw.studyflow.tv` package identity;
- TV banner geometry equivalent to 320×180 px at xhdpi (`160dp × 90dp`);
- backup and device-transfer exclusions across root/file/database/shared preferences/external storage;
- cleartext framework traffic disabled;
- conditional Release signing from the complete `STUDYFLOW_RELEASE_*` credential set, with partial configuration rejected;
- built Debug/Release APK ZIP/package inspection;
- `studyflow_tv` target isolation from phone `:app`, feature modules, `core:data`, `core:database`, and repository-wide formatting.
