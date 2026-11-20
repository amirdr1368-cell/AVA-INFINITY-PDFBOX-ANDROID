# AVA INFINITY AEMS Backup Chain v3
**Status:** Source-integrated design baseline; runtime verification pending  
**Date:** 2026-11-17  
**Owner:** Amir

## 1. Core decision

The normal recovery anchor is a complete daily full snapshot. Important changes between daily full snapshots are captured as event deltas. Weekly and monthly retention classes are labels on verified full snapshots; if their due windows coincide, one full archive may satisfy multiple labels. A milestone is always an independent full archive.

This avoids a long, fragile daily-delta chain. If an event delta is damaged, the latest daily full remains independently restorable.

## 2. Archive types and schedule

- Initial: first v3 full archive when no v3 chain exists.
- Daily: complete full snapshot, normally every 24 hours.
- Event: incremental delta after important persisted changes between daily full snapshots.
- Weekly: label on the first successful full snapshot in the weekly window.
- Monthly: label on the first successful full snapshot in the monthly window.
- Milestone: independent full snapshot before restore and owner-approved high-impact/destructive changes.
- Recovery: standalone full snapshot for an explicit recovery workflow; automatic worker does not silently re-root an unreadable chain.

A daily worker always creates a daily full. An event worker creates an event delta only while the current full baseline is younger than 24 hours, the chain has fewer than 100 event deltas, and cumulative delta bytes remain below 25% of the baseline file size. If no valid baseline exists, the baseline is at least 24 hours old, the chain reaches 100 deltas, or cumulative delta size reaches 25% of the full baseline, it creates a daily full instead. An unreadable or ambiguous existing chain blocks automatic writes and pruning; it is not bypassed using a possibly incorrect passphrase.

## 3. v3 encrypted archive format

The outer encrypted envelope remains backward-compatible with the existing AVAIBK1 envelope:
- PBKDF2-HMAC-SHA256, 210,000 iterations
- 16-byte random salt
- 12-byte random AES-GCM nonce
- AES-256-GCM with 128-bit authentication tag
- passphrase, derived key and plaintext byte buffers are cleared where practical

The authenticated inner v3 archive includes:
- format = AVA-INFINITY-BACKUP-CHAIN-3
- schemaVersion = 3
- archiveId (random immutable UUID)
- archiveType = full or delta
- checkpointKind and, for full archives, checkpointLabels
- createdAt
- parentArchiveId
- baseArchiveId
- sequence
- previousChainHash
- payloadSha256 for full archives, or parentPayloadSha256 and resultPayloadSha256 for deltas
- chainHash

A full archive is its own base, has sequence 0 and no parent. An event delta points to exactly one parent, carries the same base ID, increments sequence by one and carries the previous chain hash.

The chain hash covers the canonicalized chain header. Canonical JSON sorts object keys and normalizes numeric representations. AES-GCM authenticates the encrypted archive; SHA-256 chain hashes provide ancestry and reconstruction consistency.

## 4. Delta behavior

The current v3 delta is a deterministic top-level section replacement:
- changed top-level payload sections are included in changes;
- removed top-level sections are listed in removedKeys;
- unchanged sections are omitted;
- the resulting payload digest is verified after application.

This is a real incremental archive, but it is not yet record-level compression. If a large array changes, that entire section is stored in the delta. Record-level operations can be introduced in a later schema version.

Backup-worker maintenance events remain in the device-local AACS audit log but are excluded from the portable audit snapshot. This prevents each successful/no-op backup from changing the next snapshot solely because the previous backup wrote its own audit event. Security, access-control and restore events remain portable.

## 5. Chain validation and reconstruction

For every archive:
1. Authenticate/decrypt the encrypted envelope.
2. Validate format, schema, ID, type, sequence, timestamp and digest syntax.
3. Recalculate and verify chainHash.
4. For full archives, validate base identity, sequence 0, null parent and payload digest.
5. For deltas, validate parent ID, base ID, sequence continuity, previousChainHash and parent payload digest.
6. Apply changes/removals to a copy of the parent payload.
7. Verify resultPayloadSha256 and the complete AVA payload schema.

Reconstruction rejects duplicate archive IDs, missing parents, forks, cycles, sequence gaps, timestamp regressions, wrong parent payloads and digest mismatches. It never skips a broken delta. The last independently verified full or earlier valid restore point remains available.

## 6. Storage and atomic publication

The selected backup folder must be independently located from the primary vault:
- Local SAF destinations must resolve to a mounted removable volume whose volume ID differs from the primary vault volume.
- The phone's internal shared-storage volume and another folder on the same microSD are rejected.
- A narrow allowlist of recognized remote SAF providers is accepted as an independent remote destination; unknown providers are not assumed to be remote.
- The same policy is checked during setup, automatic backup, health checks, restore-staging checks, manual export, and milestone creation. Workers recheck it at execution time, not only during setup.

Archive layout:
- AVA_full_<timestamp>_<id>.avaibk
- AVA_delta_<timestamp>_<id>.avaibk
- AVA_milestone_<reason>_<timestamp>_<id>.avaibk
- AVA_pending_<id>.tmp.avaibk
- AVA_chain_index_<timestamp>_<id>.avacidx

One shared chain lock serializes workers, milestone publication, scans, manifests and pruning. A separate snapshot lock serializes local bucket/profile/settings mutations against portable snapshot capture.

Write protocol:
1. Verify owner context, AACS state, primary vault health and destination independence.
2. Capture one consistent payload snapshot under the shared snapshot lock.
3. Validate the AVA payload.
4. Build a full archive or event delta from the verified head.
5. Encrypt to a unique temporary file.
6. Read back, authenticate, validate the header and reconstruct the expected payload.
7. Promote to an immutable final file name.
8. Commit the AACS audit event.
9. Write and authenticate the encrypted metadata manifest.
10. Only then run dependency-aware pruning.
11. Rescan and refresh the manifest after pruning.

If archive write/read-back, audit or manifest update fails, do not prune. If the chain is unreadable or ambiguous, stop automatic writes and require owner review.

## 7. Manifest

The encrypted manifest contains only archive metadata: IDs, file names, types, checkpoint labels, parent/base IDs, sequence, previous/chain hashes, creation times, content digest and byte size.

It is a rebuildable catalog, not the source of truth. The current source writes and authenticates the manifest, compares it with the archive inventory, and rebuilds it when missing, stale or invalid. It still scans and authenticates archive files directly; using the manifest to skip unnecessary decryptions remains open.

## 8. Retention

- 30 daily full restore points
- 12 weekly full checkpoints
- 12 monthly full checkpoints
- all event deltas needed for the recent restore window and their ancestors
- milestone and explicitly labeled recovery full archives are never automatically deleted

One full file may carry more than one checkpoint label. Retention counts labels, not duplicate files. Pruning computes the dependency closure before deletion. If the graph is ambiguous, a parent is missing, or dependency analysis is incomplete, pruning stops. Old v1 legacy files are preserved rather than silently deleted.

## 9. Restore

The restore UI supports:
- folder-based selection of verified v3 full/delta restore points;
- legacy v1 full snapshots in the same folder;
- single-file import for a standalone full archive.

A delta file alone is never offered as a restore point. Folder restore reconstructs the selected archive target and validates the full payload before preview. The preview is followed by owner device-credential re-authentication and separate final confirmation.

Before mutation, AVA creates an independent v3 full milestone. It captures a rollback snapshot of data, profiles, settings and portable AACS state. Restored buckets, profiles, settings and AACS state are read back and verified. If mutation fails, rollback is applied and verified. If rollback or audit cannot be verified, voice output stops and public-only mode is entered.

## 10. Health and verification

- Weekly health worker scans and reconstructs v3 restore points and checks readable legacy v1 snapshots.
- Monthly restore-test worker stages the reconstructed chain head in isolated memory and validates it without writing to live stores.
- This staging test is not yet a separate temporary persistence adapter.
- Source JUnit tests cover chain construction, reconstruction, sequence/hash checks, force-full thresholds, destination independence, empty-delta detection, and failure cases. They are not executed under Amir's current no-test instruction.

## 11. Remaining work before AEMS-07 completion

- Use the encrypted manifest as a verified scan fast path to avoid decrypting every retained archive on each worker run.
- Record-level delta encoding for large arrays.
- Safe event debounce/coalescing. Current APPEND_OR_REPLACE queue is loss-avoiding and serialized but can schedule redundant no-op scans during event bursts.
- Fault-injection coverage for no space, media removal, interrupted archive/manifest writes, partial deletion and rollback.
- Restore into a separate temporary persistence adapter, not only in-memory JSON staging.
- Validate SAF behavior and physical volume independence on the target Android device.
- Optional second cloud replica remains a separate future capability.
- No Build, tests, CI, APK or physical-device verification has been performed.


## 12.1 Engineering refinements recorded - 2026-11-17

- Daily full runs are enforced in the worker; event jobs append deltas only between verified daily fulls.
- A full is forced if the base is missing, reaches 24 hours, reaches 100 event deltas, or cumulative delta archive bytes reach 25% of the base full file size.
- These are conservative initial guardrails, not measured performance limits.
- Unchanged event snapshots produce no published delta.
- Archive timestamps advance beyond the latest verified chain head to prevent clock rollback or timestamp ties from selecting an incorrect head.
- The repository serializes scan, index publication, and retention operations with the shared chain lock.
- Restore preview reports the combined size of the base full plus all deltas required for the selected point.
- The monthly restore staging worker reconstructs the chain head in memory; a separate temporary persistence adapter and fault-injection tests remain open.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 12.2 Clock rollback and index publication - 2026-11-17

- A negative base age (device wall clock earlier than the last verified full timestamp) now forces a new full checkpoint rather than appending a delta.
- New archive timestamps remain strictly greater than the latest verified chain head.
- Encrypted index filenames use a monotonically increasing generation prefix, so a wall-clock rollback cannot make an older index sort ahead of a newer one.
- The index is read back after final-name promotion and compared against the generated index before old index generations are pruned.
- A source test covers the negative-base-age force-full rule. Tests remain unexecuted.
