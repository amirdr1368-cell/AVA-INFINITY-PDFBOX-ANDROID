> Historical design snapshot. The current authoritative design is docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md.

# AVA INFINITY AEMS Backup Chain v2
**Status:** SUPERSEDED by docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md  
**Date:** 2026-11-17  
**Owner:** Amir

## 1. Why the design is being replaced

The previous implementation writes independent encrypted full snapshots for daily, weekly, monthly and event triggers. It verifies each file after writing, but it does not have parent-child ancestry, archive identity, chain reconstruction or dependency-aware retention. The restore UI accepts one encrypted file at a time. It is therefore a collection of snapshots, not a backup chain.

This document defines the replacement architecture. Existing AVA-INFINITY-MOBILE-BACKUP-1 files remain readable as legacy full snapshots. They are never silently rewritten or deleted during migration.

## 2. Goals and invariants

1. A successful restore point is reconstructable from one authenticated full archive plus a complete ordered sequence of authenticated deltas.
2. Every archive has a random immutable ID, creation time, schema version, archive type, parent ID and base-full ID.
3. A delta is accepted only when its parent ID and parent-payload digest match the current reconstructed state.
4. A reconstructed state is accepted only when its resulting digest and the AVA payload schema both validate.
5. The encrypted envelope remains AES-256-GCM with a fresh 16-byte salt and 12-byte nonce per file, PBKDF2-HMAC-SHA256 (210,000 iterations), and authenticated encryption. The chain digest is an additional consistency check, not a replacement for GCM.
6. A new archive is written to a temporary name, read back, decrypted, validated and only then promoted to its immutable final name.
7. A failed write, failed read-back, unavailable destination, failed manifest update or low-space condition must not prune or overwrite the last-known-good restore point.
8. Milestone archives are immutable and excluded from routine retention pruning.
9. The primary vault and portable backup destination must be on separate physical storage volumes when the primary vault is configured.
10. Device-bound Android Keystore keys, API keys, session tokens, private grants and active sessions are never exported.
11. Restore preview, explicit owner re-authentication and final confirmation are required. A pre-restore milestone and verified rollback path are mandatory.
12. Legacy v1 snapshots remain independently restorable.

## 3. Storage layout

All files live under the user-selected independent backup folder:

- AVA_chain_index.avacidx: encrypted index/manifest, rebuildable from archive metadata.
- AVA_full_<UTC timestamp>_<id>.avaibk: immutable full baseline.
- AVA_delta_<UTC timestamp>_<id>.avaibk: immutable incremental change archive.
- AVA_milestone_<reason>_<UTC timestamp>_<id>.avaibk: immutable pre-change/pre-restore full archive.
- AVA_pending_<id>.tmp.avaibk: temporary write; never a restore candidate.
- Legacy v1 files remain in place and are never automatically renamed.

File names are for discovery only. Identity, ordering and ancestry come from authenticated encrypted metadata, not lexicographic file ordering.

## 4. Archive envelope and chain model

### Full archive
A full archive contains:
- format = AVA-INFINITY-BACKUP-CHAIN-3
- schemaVersion = 3
- archiveId
- archiveType = full
- checkpointKind = initial / daily / weekly / monthly / recovery / milestone
- checkpointLabels
- createdAt
- parentArchiveId = null
- baseArchiveId = archiveId
- sequence = 0
- previousChainHash = null
- payloadSha256
- chainHash
- payload (complete portable AVA payload)

### Delta archive
A delta contains:
- format = AVA-INFINITY-BACKUP-CHAIN-3
- schemaVersion = 3
- archiveId
- archiveType = delta
- checkpointKind = event
- createdAt
- parentArchiveId
- baseArchiveId
- sequence = parent sequence + 1
- previousChainHash
- parentPayloadSha256
- resultPayloadSha256
- changes
- removedKeys
- chainHash

The first implementation uses deterministic top-level section replacement. Changed sections are included in changes; removed sections are listed in removedKeys. This is intentionally simpler and safer than record-level patching. A later schema version may add record-level operations for large arrays without changing the v2 chain contract.

### Chain reconstruction
1. Authenticate and validate the selected full archive.
2. Verify its ID equals its baseArchiveId and its payload digest.
3. Load deltas whose baseArchiveId matches the selected baseline.
4. Follow exactly one child at a time using parentArchiveId; reject forks, cycles, duplicate IDs, missing parents and ambiguous children.
5. Before each delta, compare parentPayloadSha256 with the digest of the current materialized payload.
6. Apply changes and removedKeys, then compare resultPayloadSha256.
7. Validate the resulting AVA payload schema after each applied delta and at the final restore point.
8. Never skip a missing or invalid delta to continue with later deltas. Offer the last verified restore point before the broken link.

## 5. Schedule and restore points

- Daily: a complete, independently restorable full snapshot every 24 hours. This is the normal chain reset and recovery baseline.
- Event: a delta checkpoint after important persisted changes between daily full snapshots. Event bursts may be safely coalesced into one delta containing the latest complete value of each changed section.
- Weekly: an additional full checkpoint on the first successful run in the weekly window; it may also satisfy the daily full due at that time.
- Monthly: an additional full checkpoint on the first successful run in the monthly window; it may also satisfy the daily/weekly full due at that time.
- Milestone: a full archive before restore and before owner-approved destructive/high-impact changes.

If no verified full baseline exists, or the latest daily full is older than 24 hours, or the current chain cannot be reconstructed, the next run must create a new full baseline. It must not guess a parent.

A daily, weekly, monthly, or milestone full archive resets the event-delta chain. Event deltas attach only to the newest verified full baseline. Weekly/monthly labels may share one physical full archive when their schedule boundaries coincide, provided the encrypted catalog records all satisfied retention classes.

## 6. Retention and dependency-aware pruning

Retention targets preserve the previously approved counts:
- 30 daily full restore points
- 12 weekly full checkpoints
- 12 monthly full checkpoints
- Event deltas: preserve every delta required to reconstruct a restore point for each retained daily full
- Milestones: no automatic deletion

A daily restore point is a materialized point in the chain, not necessarily a standalone full file. Pruning is performed only after a newer archive has passed authenticated read-back and chain reconstruction.

Pruning rules:
1. Never delete the newest verified full baseline.
2. Never delete an archive that is a required ancestor of a retained restore point.
3. Never delete a milestone automatically.
4. When a full baseline expires, delete its dependent deltas only if no retained restore point or milestone references them.
5. If dependency analysis is incomplete, keep the files. Storage pressure is reported; it is not solved by unsafe deletion.
6. A manifest is a cache/index. If missing or corrupt, rebuild it by scanning and authenticating archives. Do not treat a corrupt manifest as proof that archives are corrupt.

## 7. Atomic write protocol

1. Acquire the single-writer backup lock.
2. Confirm owner context, no restricted AACS session/public-only mode, healthy primary vault, independent destination and writable storage.
3. Capture one consistent immutable payload snapshot.
4. Validate the source payload.
5. Build a full or delta archive against a verified chain head.
6. Write encrypted bytes to a unique temporary file.
7. Close and flush the stream.
8. Read the temporary file back, authenticate/decrypt it, validate metadata and reconstruct the resulting state.
9. Rename the temporary file to its immutable final name.
10. Update the encrypted index using the same temp/read-back/promote protocol.
11. Record the AACS audit event.
12. Only after all required verification succeeds, run dependency-aware retention pruning.

If archive promotion succeeds but index update fails, keep the archive. Rebuild the index on the next run. If the audit write fails, do not prune; surface a protected warning and retain all verified data.

## 8. Restore and rollback protocol

- User chooses a restore point, not an arbitrary delta file.
- Preview displays the baseline date, target date, archive count, chain length, approximate size, health and recoverable categories.
- Owner device credential re-authentication and a separate final confirmation are mandatory.
- Create and authenticate an independent full pre-restore milestone.
- Reconstruct the selected chain into isolated memory/staging and validate the complete payload before touching live stores.
- Capture the current in-memory rollback snapshot of buckets, profiles, settings and portable AACS state.
- Apply the staged payload, read back every bucket/profile/settings/AACS section and verify it.
- If any mutation fails, restore the rollback snapshot and verify it.
- If rollback or security audit cannot be verified, stop voice output and enter public-only safe mode.
- Never overwrite the current state with an unverified or partially reconstructed chain.

## 9. Failure handling

- Wrong passphrase / GCM failure: reject archive; no writes.
- Missing parent / fork / cycle / digest mismatch: mark chain broken; expose only the last verified point; do not skip links.
- Destination removed / read-only / no space: retain old files, record failure, retry later; do not prune.
- Interrupted temporary write: ignore it as a restore point; remove only after a conservative stale-age check.
- Manifest missing/corrupt: rebuild from authenticated archive metadata.
- Primary vault unsafe or queued writes: pause portable backup until the source state is consistent.
- Backup key/passphrase unavailable: stop; do not create plaintext fallback.
- Audit failure: preserve data and disable pruning; surface a protected warning.

## 10. Migration and compatibility

- v1 files with AVA-INFINITY-MOBILE-BACKUP-1 payload remain full restore points.
- The earlier v2 helper format is superseded before runtime integration; the current source helper defines AVA-INFINITY-BACKUP-CHAIN-3.
- First v3 run creates a daily full baseline. It does not convert, rename or delete v1 files.
- The restore selector must list v1 full snapshots alongside v3 reconstructed points.
- No v2/v3 chain archive has been established as a deployed/runtime format; the current app worker still writes v1 full snapshots.
- The encrypted envelope's current PBKDF2/AES-GCM parameters remain unchanged; future KDF changes require explicit version negotiation and migration.

## 11. Acceptance criteria before AEMS-07 can be marked complete

- Full archive creation, authenticated read-back and immutable promotion.
- Delta creation and deterministic reconstruction.
- Missing parent, fork, cycle, duplicate ID, tampered digest and wrong-key rejection.
- Manifest rebuild from files.
- Dependency-aware retention with milestone protection.
- No deletion/pruning after any write, verification, manifest or audit failure.
- Restore preview from a selected point across multiple deltas.
- Staging restore and rollback fault-injection coverage.
- Separate physical destination validation.
- Storage removal, no-space and interrupted-write scenarios.
- Legacy v1 restore compatibility.
- Only after source audit: owner-authorized Build, tests, CI and device acceptance. Those verification steps remain disabled by Amir's current instruction.

## 12. Current implementation boundary

This document is the approved working design baseline for implementation. It does not claim the chain engine, manifest, delta scheduler, chain-aware restore selector or acceptance tests are implemented. The current source still writes independent v1 full snapshots until the v2 engine is integrated and audited.


## 13. Chain v3 integrity correction - 2026-11-17

The prior v2 pure helper was present in source but not integrated into the backup worker. Review found that it did not enforce contiguous sequence numbers or bind each archive to the previous chain hash. It also did not reject a delta built against a payload that differed from the parent archive.

The source helper is now advanced to chain schema v3:
- Full archives support daily, weekly, monthly, recovery, initial, and milestone checkpoint kinds.
- Daily full is the baseline required by the approved AEMS-07 policy; event deltas are incremental checkpoints between full snapshots.
- Every archive includes a strict sequence number, previous-chain digest, and a deterministic chain-header digest.
- Delta creation verifies the supplied parent payload against the parent archive before generating a child.
- Delta application verifies parent ID, base ID, contiguous sequence, previous chain digest, parent payload digest, result payload digest, and resulting schema.
- Canonical JSON hashing sorts object keys and normalizes numeric representations.
- Reconstruction rejects duplicate IDs, missing parents, forks, cycles, and invalid digest/sequence links.
- JUnit source tests cover daily full checkpoints, contiguous event sequences, wrong parent payloads, and tampered chain headers. Tests are not executed.

This is still pure chain logic only. The encrypted backup worker, catalog, restore selector, and live restore path have not yet been switched to v3. Existing v1 encrypted full snapshots remain the active source format until integration and compatibility work are complete.

## 14. Current implementation boundary correction

The source-level chain helper is not a complete backup system. The following remain open:
- Integrate v3 archives into encrypted write/read-back flow.
- Add an encrypted catalog with crash-safe commit and rebuild-from-files behavior.
- Serialize daily/event/weekly/monthly/milestone work through one chain writer.
- Implement dependency-aware retention for delta ancestors.
- Make restore selection chain-aware while retaining v1 restore support.
- Implement isolated temporary persistence restore with fault injection.
- Verify destination independence consistently for every backup path and supported SAF provider.
- Optional second cloud replica.

No Build, tests, CI, APK, or physical-device verification was performed.


## 15. Chain v3 worker and restore integration - 2026-11-17

This section supersedes the earlier statements that v3 existed only as a pure helper or that the worker still wrote only v1 full snapshots.

Implemented in source:
- AvaEncryptedBackup now reads authenticated v1 full payloads, v3 full/delta archives, and the encrypted chain index format.
- AvaBackupWorker writes v3 daily full archives. Event-triggered jobs write v3 section-level deltas against the latest verified chain head. If no baseline exists or the baseline is at least 24 hours old, the worker creates a new full archive instead of an unanchored delta.
- Daily full archives carry daily labels and may also satisfy weekly/monthly labels when those checkpoints are due.
- Event deltas with no changed sections are skipped, avoiding empty chain links.
- Worker and milestone publication use a shared chain lock; cross-bucket snapshot capture uses the snapshot lock.
- Archive timestamps are advanced beyond the latest verified chain timestamp to avoid clock rollback/tie ambiguity.
- The ordinary worker rejects a backup folder on the same detected physical volume as the primary vault and requires read/write access.
- AvaBackupChainRepository scans authenticated v3 files, validates the graph, reconstructs restore points, detects missing parents/forks/cycles, writes an encrypted rebuildable index, and performs dependency-aware retention.
- Retention now keeps the newest 30 daily fulls, 12 weekly full checkpoints, 12 monthly full checkpoints, all milestones, and the delta ancestors required by retained recent restore points.
- MainActivity offers reconstructed chain restore points and legacy v1 full files together. The restore preview reports the total bytes of the selected chain, not only the final delta file.
- Manual exports and pre-change milestones use v3 full archives. Legacy v1 files remain independently restorable and are not silently rewritten.

Remaining:
- The encrypted index is currently written and verified but not read as a fast-path by scan; scan rebuilds its view from authenticated archive files. A manifest/index fast-path and explicit repair UI remain future work.
- The current chain format uses section/bucket-level replacement, not item-level operations.
- The proposed 100-delta and 25%-of-base thresholds are not implemented; the enforced full-reset boundary is the 24-hour base age and the daily full schedule.
- Event jobs are serialized by a shared chain lock, but WorkManager still queues separate event triggers; empty deltas are skipped rather than a dedicated debounce/coalescing worker.
- Restore reconstruction is performed in memory and the existing live-store rollback snapshot is used. A separate temporary persistence adapter with injected storage faults is not yet implemented.
- Optional cloud replication is not implemented.
- JUnit sources exist but were not executed.

No Build, tests, CI, APK, or physical-device verification was performed.


## 16. Daily full, event delta, and retention correction - 2026-11-17

- Corrected the worker schedule to create a complete daily full archive on every daily WorkManager run. Daily jobs can no longer create a delta.
- Event jobs create section-level deltas only when a verified base full is younger than 24 hours. If the base is missing or 24 hours old, an independent full checkpoint is created.
- Initial full archives also receive the daily retention label. Weekly/monthly labels are added when their current period has no verified checkpoint.
- Removed the volatile createdAt field from the portable payload; archive creation time belongs to the authenticated archive header. This allows unchanged data to produce an empty delta.
- Empty event deltas are skipped rather than published.
- Full/delta/milestone timestamps are made monotonic relative to the verified chain head, avoiding a system-clock rollback or equal-timestamp tie from selecting the wrong head.
- Dependency-aware pruning now explicitly retains the newest 30 daily fulls in addition to 12 weekly and 12 monthly checkpoints, all milestones, and delta ancestors needed by retained restore points.
- Repository scan marks an invalid full root as corruption as well as an invalid delta, preventing pruning when any chain archive cannot be verified.
- Backup worker, health worker, restore-test worker, folder setup, and milestone creation reject a backup location on the same detected physical volume as the primary vault.
- Restore preview reports the total size of the reconstructed full-plus-delta chain.
- Weekly health worker now scans/reconstructs v3 chains and separately checks legacy v1 archives. Monthly restore test reconstructs the current chain head in isolated memory; it still does not exercise a separate persistence adapter.

Remaining gaps are unchanged: index/manifest is written and authenticated but not read as a fast path; 100-delta/25%-size force-full thresholds are not implemented; event jobs are serialized but not coalesced by a dedicated debounce worker; separate temporary persistence adapter/fault injection and cloud replica remain open. No Build, tests, CI, APK, or device verification was performed.


## 17. Independent destination policy refinement - 2026-11-17

- Added a pure destination policy and source adapter check.
- Local SAF backup targets are accepted only when StorageManager identifies the selected volume as removable and mounted, and its volume ID differs from the primary vault volume.
- The phone's internal shared-storage volume and a second folder on the same microSD are rejected.
- A small explicit allowlist of recognized remote SAF providers may be used as an independent remote destination; unknown providers are not assumed to be cloud/remote.
- Automatic backup, weekly health check, monthly restore-staging worker, folder setup, manual export, and milestone creation all apply the same destination policy.
- The encrypted index now records the latest verified archive ID, base ID, sequence, and chain hash. It remains a verified rebuildable index; scan still reconstructs from archive files rather than trusting the index as a fast path.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 18. Force-full thresholds and destination safeguards - 2026-11-17

- The automatic worker now forces a new full archive when the event chain reaches 100 deltas or cumulative delta file bytes reach 25% of the base full archive size, in addition to the 24-hour base-age and daily schedule rules.
- These are conservative initial guardrails implemented in source. They are not measured performance limits; no benchmark or runtime test has been run.
- The chain repository serializes scans, manifest publication, and pruning with the shared chain lock.
- The encrypted index now records the latest verified archive ID, base ID, sequence, and chain hash. The scan path still rebuilds from authenticated files and does not trust/read the index as a fast path.
- Independent backup destinations are now limited to a separate mounted removable volume or an explicitly recognized remote SAF provider. Internal shared storage, same-volume folders, and unknown providers fail closed.
- This policy is applied to automatic backup, health, restore-staging, folder setup, manual export, and milestone paths.
- No Build, tests, CI, APK, or physical-device verification was performed.
