# StudyFlow Remediation Overlay 14 of 30

Overlay 14 implements Android receiver, deep-link, widget and device-target durability after successful OV13 commit `cc95602e`.

## Scope

- Receiver-bound phone↔TV credentials and authenticated-send rejection for mismatched selected receivers.
- Intent-persisted reminder launches so process death does not erase destination context.
- Persistent receiver mutation journal around reminder and widget BroadcastReceiver work.
- Configured-plan widget action semantics for `START_NEXT`.
- Collision-proof TV domain-action journal and SFLP keys.

## Validation wiring

`verifyOv14AndroidReceiverWidgetDurability` is added to the root Gradle verification graph. `.devtool-artifact/validation/validate.sh` invokes that task during artifact apply and contains no daemon-stop command.
