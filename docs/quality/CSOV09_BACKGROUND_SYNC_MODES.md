# CSOV09 Background sync modes

StudyFlow now models optional background sync without making cloud mandatory. Fresh installs still
have effective background sync off, while configured sync defaults to manual only.

Supported modes are Off, Manual only, Upload after changes, Check daily, and Full automatic.
Automatic modes are gated behind an explicit provider, encrypted remote backups, network access,
Wi-Fi preference, battery-not-low, and optional charging-only constraints.
