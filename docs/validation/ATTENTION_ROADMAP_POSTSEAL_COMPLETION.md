# Attention roadmap post-seal completion validation

`verifyAttentionRoadmapPostsealCompletion` is the terminal gate after the ATT01–ATT12 roadmap re-audit. It exists specifically to prove production reachability that older token/model-oriented verifiers could miss.

The task depends on `verifyAtt12AttentionReleaseFinalSeal` and then runs `scripts/verify_attention_roadmap_postseal_completion.py`. The post-seal verifier rejects local-only ATT07 candidate patterns, synthetic Attention device IDs, missing Android/Desktop/TV inbound handlers, mirror-suppression after a remote owner succeeds, compact-only paths that post a system notification, orphan ATT08/ATT09 native bridges, text-only Android calendar sharing, fabricated TV calendar timestamps, unsurfaced TV ATT10/ATT11 projections, and stale capability-matrix claims.

The final Devtool application should keep validation enabled and include this task plus the ATT12→ATT01 explicit gates. The artifact-generation sandbox can run the Python/Node/TypeScript/source/release portions but cannot download the uncached Gradle 9.7.1 distribution, so the Android/Desktop/TV JVM compilation lanes remain authoritative on the user's Termux host.
