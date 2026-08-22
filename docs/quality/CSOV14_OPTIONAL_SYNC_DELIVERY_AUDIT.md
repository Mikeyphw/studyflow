# CSOV14 optional sync delivery audit hotfix

This hotfix closes the post-CSOV13 delivery-audit gap.

## Gap found

The CSOV00-CSOV13 source verifiers existed, but the cumulative Gradle surface did not expose every promised CSOV verifier from `checkAll`. In particular, the CSOV00/CSOV01 verifier script existed without a matching `verifyCloudOptionalSyncSettings` Gradle task, and the optional sync verifiers were not part of the all-up verification lane.

## Hotfix

- Adds `verifyCloudOptionalSyncSettings` as a Gradle `Exec` task.
- Adds `verifyCsov14OptionalSyncDeliveryAudit` as a cumulative CSOV delivery gate.
- Wires `checkAll` to every CSOV verifier from CSOV00/CSOV01 through CSOV14.
- Adds a cumulative script that runs all CSOV verifiers and checks current product surfaces for local-first optional-cloud language.

## Acceptance

Run:

```bash
./gradlew verifyCsov14OptionalSyncDeliveryAudit
```

For full repository coverage, `checkAll` now includes the CSOV verification lane.
