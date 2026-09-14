#!/system/bin/sh
set -eu
# Source-level OV16 closure gate. The Gradle task that invokes this script owns
# native compilation/unit tests; this script intentionally does not start, stop,
# or nest another Gradle invocation.
python3 scripts/verify_ov16_attention_scheduler_wakeups.py
