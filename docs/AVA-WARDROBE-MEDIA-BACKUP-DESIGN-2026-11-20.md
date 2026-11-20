# AVA INFINITY - Wardrobe Media Backup Design

Status: IMPLEMENTATION IN PROGRESS  
Date: 2026-11-20  
Target branch: `ava/mobile-app-integration`  
Implementation status: NOT IMPLEMENTED; no build or workflow run is authorized by this document.

## 1. Purpose

Provide a portable, encrypted backup and restore path for owner-added wardrobe images currently stored under the app-private `filesDir/ava-wardrobe` directory and indexed by the encrypted `ava_ui` preference key `owner_wardrobe_images`.

The existing portable JSON backup and backup-chain format remain unchanged. Media bytes must not be base64-embedded in the JSON payload or delta chain.

## 2. Current behavior and limitation

- Imported images are copied into app-private internal storage, with a 15 MiB per-image import limit.
- The image index is stored in `AvaSecurePrefs(this, "ava_ui")`.
- The current portable backup payload includes memories, profiles, tasks, vocabulary, AACS portable state and selected settings.
- The current payload does not include the `owner_wardrobe_images` index or files under `ava-wardrobe`.
- Consequently, app uninstall/data-clear or migration to another device can lose these imported images unless the owner has retained separate copies.
- The wardrobe screen must continue to disclose this limitation until media export and restore are implemented and tested.

## 3. Proposed artifact

Create a separate versioned artifact named conceptually `AVA-WARDROBE-MEDIA-1`. It is a companion to, not a replacement for, the existing encrypted backup.

The artifact contains:
- A strict manifest with schema version, creation time, bundle ID, item count, total uncompressed bytes and per-item metadata.
- For each item: stable item ID, safe generated filename, category key, display name, MIME type, byte length, SHA-256 digest and added timestamp.
- The original image bytes, without relying on external URLs or remote image hosting.

Do not serialize absolute device paths, content-provider URIs, Android document IDs, or device-specific encryption keys into the manifest.

## 4. Encryption and integrity

- Use authenticated encryption (AES-256-GCM) over the complete media container, including manifest and image bytes.
- Derive a portable encryption key from the owner's backup passphrase using PBKDF2-HMAC-SHA256, a fresh random 16-byte salt, and a versioned iteration count. Keep the media format's KDF parameters explicit and validated.
- Generate a fresh random 12-byte GCM nonce for every export. Never reuse a nonce with the same derived key.
- Authenticate a fixed format/version header as GCM additional authenticated data.
- Zero temporary key and passphrase buffers in `finally` blocks.
- Do not reuse the Android Keystore device key as the portable encryption key: it cannot be transferred safely to another device.
- Do not claim a bundle is authenticated until the complete GCM tag has been verified.

## 5. Resource and parser limits

Initial conservative limits for implementation and tests:
- Maximum item count: 200.
- Maximum individual image size: 15 MiB.
- Maximum aggregate uncompressed image bytes: 100 MiB.
- Maximum manifest size: 1 MiB.
- Maximum encrypted bundle size: 110 MiB plus a small documented envelope allowance.
- Accept only supported image MIME types and verify image decoding bounds; do not trust filename extensions or declared MIME type alone.
- Reject duplicate item IDs, duplicate archive paths, path traversal, absolute paths, separators in generated filenames, unknown schema versions, missing fields, integer overflow and size/count mismatches.
- Enforce limits while streaming, not only from manifest declarations.
- Never extract arbitrary ZIP paths. Prefer a deterministic entry format or a tightly constrained archive reader that accepts only the exact generated entry names.
- Avoid loading the entire collection into memory. Stream encryption/decryption and file copying; decode only bounded thumbnails for preview.

These limits are design defaults and must be reconciled with Android storage and available-memory tests before implementation.

## 6. Export flow

1. Require the same strict owner session policy used by wardrobe image import: active approved owner session, owner identity, required session level, and not public-only mode.
2. Require successful AACS audit logging before sensitive export begins.
3. Read the encrypted preference index, validate every record, and canonicalize each file path under `filesDir/ava-wardrobe`.
4. Refuse export if any indexed file is missing, escapes the private directory, exceeds limits, or fails image validation. Do not silently omit damaged items.
5. Stream each file into the versioned container while calculating SHA-256 and enforcing count/size limits.
6. Encrypt the complete container using a fresh salt and nonce, then write to a user-selected destination using a temporary document when supported.
7. Verify the completed output can be reopened and authenticated before reporting success.
8. Audit success/failure without logging image bytes, passphrases, private names, or full paths.

## 7. Preview and restore flow

1. Require strict owner authorization and successful audit logging.
2. Read the selected bundle with a hard encrypted-file size cap.
3. Decrypt into a private staging directory only. Do not alter the live wardrobe directory during parsing or authentication.
4. Verify the GCM tag before trusting any decrypted content.
5. Validate the manifest, all limits, unique IDs and safe generated filenames.
6. Stream each image to staging while recomputing its byte length and SHA-256. Verify image headers and bounded decode dimensions.
7. Present a preview with item count, category counts, total size and any conflicts. Do not expose private-category details outside the owner session.
8. Require explicit owner confirmation before applying the restore.
9. Resolve conflicts deterministically by stable item ID. The initial implementation should default to a safe merge: identical ID plus identical digest is a no-op; identical ID plus different digest is a conflict requiring an explicit keep-local or replace choice. Never silently overwrite.
10. Build a complete next index and staged media directory, validate every referenced file, then apply through a recoverable swap protocol. Preserve the previous directory and index until the new state is verified. If any step fails, restore the previous state.
11. Commit the encrypted preference index only after all files are in place and verified. Clean up staging and obsolete files only after the commit succeeds.
12. Record restore result in AACS audit history. If audit logging fails, stop before applying changes.

## 8. Recovery and lifecycle

- Export must not delete or modify source images.
- Restore must not delete local images by default.
- Failed or interrupted restore must leave the current wardrobe usable.
- A startup recovery check should detect abandoned staging/rollback directories and recover conservatively; it must never guess which incomplete set is authoritative.
- App uninstall still removes app-private files. The user must retain the exported companion bundle in a separate location.
- A successful media restore must be followed by a read-back integrity check.
- Existing JSON backup and backup-chain compatibility must remain intact.

## 9. Required tests before enabling the feature

- Valid export/import round trip with multiple image types.
- Wrong passphrase, tampered ciphertext, truncated file and invalid GCM tag.
- Manifest tampering, digest mismatch, length mismatch, duplicate IDs and unsupported schema.
- Missing indexed file, path traversal, absolute path, symlink/canonical-path escape where applicable.
- Per-file, aggregate, item-count, manifest and encrypted-file size boundaries, including integer-overflow cases.
- Corrupt image headers and decompression/dimension bombs.
- Duplicate-identical item merge and same-ID/different-digest conflict.
- Storage-full, permission-revoked, process interruption and audit-log failure during export and restore.
- Rollback verification: after every injected restore failure, the pre-restore index and images remain usable.
- Memory profiling on the supported Redmi Note 11 class device; no whole-library byte-array buffering.
- Regression tests proving the existing JSON backup, chain reconstruction, preview and restore still work unchanged.

## 10. Explicit non-goals

- No cloud upload or remote image hosting.
- No transfer of Android Keystore keys between devices.
- No change to the existing backup-chain JSON schema.
- No automatic deletion of local images after export.
- No claim that wardrobe images are backed up until export, authentication, restore and regression tests pass.

## 11. Implementation gate

This document records a design only. Implementation should begin only after the archive format, limits, streaming strategy, conflict behavior and rollback protocol are reviewed against the current backup worker and restore UI. The feature must be completed and tested before any final APK build or GitHub Actions run.

## 12. Source-code integration findings (reviewed 2026-11-20)

The current implementation was reviewed before any media feature code was written:

- `wardrobePage()` blocks the complete wardrobe screen unless there is an active, approved owner session at level 1 and public-only mode is off.
- `showAddWardrobeImageDialog()` and `saveOwnerWardrobeImage()` independently repeat the same strict owner-session predicate. A media export/import entry point should use a shared helper matching this predicate and re-check authorization immediately before file access and before commit.
- Do **not** reuse `ownerStorageActionAllowed()` for wardrobe media. Its current behavior rejects an active session and permits the no-active-session state, which is materially different from the wardrobe's strict owner-session requirement.
- The current encrypted JSON backup is deliberately bounded at 50 MiB maximum envelope and 35 MiB plaintext. Its implementation materializes JSON and ciphertext byte arrays and Base64-encodes the ciphertext. The media bundle must remain separate and streaming; do not increase these existing limits to accommodate photos.
- The backup worker captures `backupPayload()`, validates it, writes an encrypted archive to a temporary document, authenticates it by reading it back, reconstructs the chain and compares the resulting payload digest before promoting the archive. Media export should adopt the same temporary-write/read-back/promote discipline, but with streaming and media-specific integrity checks.
- The existing wardrobe image index stores display name, category, category label, canonical absolute file path and added timestamp. It does not currently store a stable item ID, MIME type, byte length or digest. The absolute path must never leave the device. For backward compatibility, the exporter may derive a portable stable item ID from the generated `wardrobe_<UUID>.<ext>` basename after validating the canonical path, while the next index schema should add explicit item ID and validated metadata.
- Import currently infers the destination extension from the provider MIME type and defaults unknown MIME types to `.jpg`; it enforces a 15 MiB streaming size cap but does not verify the actual image signature before indexing. The media exporter must inspect actual content and reject mismatched/unsupported formats. A separate hardening task should make future imports verify the image format before committing the index.
- The wardrobe UI decodes bounded thumbnails (maximum 1200 px on the longest edge), but this is not a substitute for validating full image dimensions and format during export/restore.
- The current restore flow has a pre-change milestone and rollback for JSON/profile/settings/AACS state. It does not manage wardrobe image files. Media restore must remain a separate transaction with its own staging, swap and rollback; it must not be inserted into the existing JSON restore mutation sequence.

## 13. Updated implementation order

1. [x] Extract and test one shared strict owner-session authorization helper for wardrobe media operations, without changing the behavior of existing backup/export controls. (Source and tests are present; tests not yet executed.)
2. [x] Define and integrate a versioned, non-destructive metadata enrichment migration that adds stable item IDs and verified media metadata while preserving old entries and their files. (Source and tests are present; tests not yet executed.)
3. [x] Harden image import to validate actual file signatures and bounded dimensions before the index commit. (Source and tests are present; tests not yet executed.)
4. Implement streaming bundle encode/decode and integrity verification in an isolated media component.
5. Implement export with temporary destination, read-back authentication and atomic promotion.
6. Implement preview, conflict handling and staged restore with rollback independent of the JSON restore path.
7. Add failure-injection tests and device memory/storage tests.
8. Only after all static review and tests pass, consider the single final APK build and workflow run.

No source-code implementation was changed as part of this review.

## 14. Implementation progress (2026-11-20)

Completed on `ava/mobile-app-integration`:
- Added `AvaWardrobeMediaPolicy`, a pure shared policy requiring public-only mode off, an active session, an approved session, level 1 and person ID `owner`.
- Replaced the three duplicated strict owner checks in `wardrobePage()`, `showAddWardrobeImageDialog()` and `saveOwnerWardrobeImage()` with the shared policy.
- Added `AvaWardrobeMediaPolicyTest` covering the allowed owner case and rejection of missing/unapproved sessions, public-only mode, other identities and other privilege levels.
- Fetched the committed files back from the branch and verified the policy implementation, tests and all three call sites are present.

Not yet completed:
- The unit tests have **not** been executed; no Gradle test, APK build or GitHub Actions workflow has been run.
- No media bundle encryption, export, import, staging or restore code has been implemented.
- Existing image import still needs actual image signature/dimension validation and a versioned metadata/index migration before portable media export can safely proceed.

This small authorization refactor does not change the behavior of the general backup controls and does not make wardrobe images portable yet.


## 15. Import validation hardening progress (2026-11-20)

Implemented on `ava/mobile-app-integration`:
- Added pure Kotlin `AvaWardrobeImageFormat` signature detection for JPEG, PNG, GIF87a/GIF89a and WebP. Unsupported/truncated signatures are rejected.
- Added JVM unit tests for supported signatures, both GIF variants, malformed RIFF/WebP markers and unknown/truncated inputs.
- Changed owner image import to stream into a temporary `.pending` file with the existing 15 MiB cap, flush it to disk, detect the actual format from file bytes, and use the detected extension rather than trusting the provider MIME type.
- Added Android bounds-only decode validation before indexing: dimensions must be positive, each edge at most 12,000 pixels, and total pixels at most 40 million.
- The validated temporary file is renamed within the private wardrobe directory before its path is written to the encrypted preference index. Failed validation leaves the existing index unchanged and the pending file is deleted by the existing failure path.

Commits:
- `1ad375c31b68a51ee404edee960617077f42f8a8` image signature detector
- `7b3c3a46d065a3457de52e315655f73708e43cea` detector unit tests
- `4b0a1ed07d45072d34340939a667104906b201e5` import validation integration

Verification status:
- Source changes were committed through GitHub and are pending a final source read-back review.
- Unit tests have not been executed. No Gradle test, APK build or GitHub Actions run has been performed.
- Header detection plus Android bounds and sampled-pixel decoding is an import hardening step, not a complete hostile-file decoder sandbox. The portable media backup/restore feature remains unimplemented.


### Additive metadata for newly imported images

A follow-up source change adds portable metadata to newly imported index records while retaining all existing legacy fields:
- `itemId`: generated UUID corresponding to the safe generated filename.
- `mimeType`: detected from the actual image signature.
- `byteLength`: measured during the bounded streaming copy.
- `sha256`: calculated incrementally during that same copy.
- The device-local canonical `file` path remains local-only and must never be exported.

This is a backward-compatible additive write for new records; no automatic rewrite of existing legacy records has been performed. Existing entries remain readable and will need validation/metadata derivation during a future explicit migration or export.

Commit: `114b7683f431f4a2b5c5f716ced8f582f016f126`.

Tests and build remain unexecuted.


## 17. Wardrobe image save transaction hardening (2026-11-20)

Implemented on `ava/mobile-app-integration`:
- The owner image save flow now records a dedicated audit authorization event before committing the updated encrypted preference index.
- The strict owner-session policy is re-evaluated immediately before the sensitive index commit, so a session change during file processing stops the operation.
- The accepted image filename pattern now requires a canonical UUIDv4 structure, not merely 36 hexadecimal-or-hyphen characters; a malformed-UUID rejection case was added.
- Failure-path audit logging is best-effort and cannot throw a second exception out of the UI save handler.
- The previous index string is retained during the operation. The new index is committed and immediately read back for exact equality verification.
- If the index commit/read-back fails, the code attempts to restore the exact previous index and verifies that rollback.
- If rollback cannot be verified, the code checks whether the live index still references the newly finalized image. It preserves that file when a reference remains, rather than deleting a file that the index may still point to.
- This removes the previous ordering hazard where an audit failure after index commit could trigger image deletion while leaving a dangling index record.

Verification:
- Updated source and tests were fetched back from GitHub after commit and inspected.
- Unit tests, Gradle tasks, APK build and GitHub Actions remain unexecuted.
- This is a local save-transaction hardening only. It does not implement media export, encrypted media backup, or restore.

## 16. Private file-path validation hardening (2026-11-20)

Implemented:
- Added `AvaWardrobeMediaPolicy.isSafeImageFile(root, candidate)`.
- It canonicalizes both paths, requires the image to be a regular file directly inside the canonical private wardrobe directory, accepts only the app-generated UUID filename pattern and supported image extensions, and enforces the existing 15 MiB per-image storage limit.
- The wardrobe gallery now uses this shared validator before previewing an indexed file. Canonicalization failures and unsafe/missing paths are skipped without modifying the index or deleting files.
- Added JVM tests for valid generated files, arbitrary names, paths outside the private directory, sibling-prefix directory traversal, missing files and oversized files.

Commits:
- `630ea780690550d434d96e0ed884d5c5707741f6` gallery uses shared path validation
- `c965e95b53e7789b4ae5adade22415cb1996a806` canonical path policy
- `9c8c1f15bff7a284ab7a2a91355bae08a9805054` path policy tests

Compatibility note:
- Existing legacy index records are not rewritten or deleted. The current gallery remains able to display legacy entries when their stored path resolves to an app-generated, bounded file in the private wardrobe directory.
- A full metadata migration for legacy entries is still pending. It must be all-or-nothing or staged, preserve every original record, and never infer trusted metadata from an unvalidated file.
- No tests have been executed and no build/workflow has been run.


## 18. Non-destructive legacy wardrobe metadata migration (2026-11-20)

Implemented on `ava/mobile-app-integration`:
- Added `AvaWardrobeMediaMigration` to enrich legacy owner image-index entries with missing `itemId`, detected `mimeType`, actual `byteLength`, and SHA-256 metadata.
- Migration only inspects app-generated UUIDv4 image files that pass the canonical private-directory policy, size bound, recognized byte signature, and filename-extension match.
- Existing record fields, names, categories, timestamps, file paths, and array entries are retained. Unsafe, missing, malformed, or unsupported legacy file references remain untouched; no image file is deleted or renamed.
- Migration is idempotent. When no record is enriched, the original JSON string is returned unchanged and no preference write occurs.
- The owner-gated wardrobe page invokes migration. It rechecks the owner session before commit, requires an audit authorization event, verifies the preference write by read-back, and attempts an exact rollback if commit verification fails.
- Added JVM tests for metadata enrichment and field preservation, SHA-256, idempotence, unsafe/missing references, extension mismatch, and unchanged-index no-op behavior.

Verification status:
- New source, integration, tests, and documentation were fetched back from GitHub and inspected.
- Gradle tests, compile, APK build, and GitHub Actions have not been run, by design.
- This migration enriches local metadata only. It does not create a media backup or implement restore.



## 19. Backup and restore integration review (2026-11-20)

The current backup implementation was re-read before beginning media bundle work:
- `AvaEncryptedBackup` uses AES-GCM with PBKDF2-HMAC-SHA256, a random salt and nonce, and the existing JSON envelope. It materializes JSON plaintext, ciphertext, and Base64 envelope data in memory, so it is not suitable for image collections.
- `AvaBackupWorker` writes a temporary encrypted archive, authenticates it by reading it back, verifies archive identity, reconstructs the chain and compares the reconstructed payload digest before promoting to an immutable filename. The media companion must use a separate streaming implementation and must not be inserted into the JSON archive chain.
- `AvaBackupChainRepository` manages encrypted archive indexes and dependency-aware retention. The media companion should have its own versioned artifact and lifecycle; JSON chain pruning must never delete or invalidate media bundles.
- The current restore UI in `MainActivity` previews authenticated JSON payloads, obtains owner approval, creates a pre-change milestone, restores data/profile/settings/AACS state, verifies read-back, and attempts rollback on failure. It does not stage or swap wardrobe media.
- Therefore, media restore must remain an independent transaction with its own staged directory, next-index validation, owner confirmation, rollback state and recovery behavior. Do not add wardrobe image replacement to the existing JSON restore mutation sequence.
- The current backup worker rejects public-only mode and active sessions. Media export must use the stricter wardrobe owner-session policy and re-check authorization before accessing private image files and before publishing an output.

No media encryption, export, import, restore, or user-interface controls were added during this review. No Gradle tests, build, or Actions run were started.



## 20. Streaming encrypted media bundle codec (2026-11-20)

Implemented on `ava/mobile-app-integration` as an isolated codec only:
- Added binary companion format `AVA-WARDROBE-MEDIA-1`, separate from the existing JSON backup chain.
- Uses AES-256-GCM with PBKDF2-HMAC-SHA256 (210,000 iterations), a fresh 16-byte salt, fresh 12-byte nonce, and the complete fixed header as authenticated additional data.
- Streams image bytes through the cipher. The manifest contains generated filenames and metadata only; device-local absolute paths and provider URIs are never serialized.
- Enforces 200 items, 15 MiB per image, 100 MiB aggregate uncompressed image data, 1 MiB manifest, and a bounded encrypted input size.
- Validates UUIDv4 IDs, category keys, generated filenames, image signatures, filename-extension/MIME consistency, lengths, SHA-256 digests, duplicate IDs/names, and aggregate sizes.
- Decryption writes only to a newly created private staging directory. A dedicated streaming input wrapper calls `Cipher.doFinal()` explicitly and propagates GCM authentication failures before a staged preview is returned. Any failure removes the newly created staging directory.
- The decoder requires a caller-supplied platform image-validation callback. `AvaWardrobeAndroidImageValidator` now checks BitmapFactory bounds (maximum 12,000 px edge and 40 million pixels) and performs a sampled decode capped around a 1,200 px edge. The same validator is used when saving new owner images.
- The callback is mandatory rather than defaulted to permissive behavior; a caller that cannot validate staged image dimensions must fail closed.
- The codec never changes the live wardrobe index or files. Applying a restore is implemented as a separate additive transaction; UI wiring and runtime verification are tracked below.

Commits:
- `8670009d87b4130cd422deaf5797946db945ec6c` streaming AES-GCM codec
- `696ba98c1dd4d4450ec902a11ee1edfed497af42` initial codec tests
- `cbc99ad3181c79d6bd6653b49c7895c8d4e8c21d` key cleanup and mandatory platform validation callback
- `57c48824eddbb5e0066f9d558f7286045582f103` bounded Android image decoder validator
- `1b326322fabf6eae82a5932ca4bebcf978d0b3b5` apply decoder validation when saving owner images
- `046bda95d3e5665e60bb649104fd20bb4da0c6d2` explicit AES-GCM final-tag verification during streaming decryption
- `19368acaa563da887f66cc7eefb19851e503919d` owner-only export and authenticated restore-preview UI
- `4a9cddc6bb38be1c04d0a2597e85e0bf6de70914` passphrase buffer clearing literal correction
- `af49e40254499f1ca97d3e12c74fc1685e9e74aa` document export and preview UI behavior
- `c6ae23de20c6b2d25176b4f4d8d41116a68d3894` export cleanup and non-empty bundle guard
- `8b22bc6fc364f5921d772b79d79815c47d9970f1` preview line breaks and SAF cancellation secret cleanup
- `e1d99172a071fc03ef3c39dac9e336b491c8d350` close temporary export stream on all validation paths and re-check owner after read-back
- `0bea2f50a3f4618dad84d94b8128bef4f2cd03fc` explicit test callback updates

Verification status:
- Codec and tests were fetched back from GitHub and inspected.
- Six JVM tests are present for multi-format round trip, wrong passphrase, authentication-tag tampering, truncation, unsafe export path, duplicate IDs, and platform decoder rejection cleanup.
- Tests have not been executed. No Gradle task, APK build or GitHub Actions workflow has been run.
- Owner-only export and authenticated restore-preview UI are now wired to the codec; applying a restore is still not implemented.


### Owner-only export and authenticated preview UI

The wardrobe page now exposes two separate actions, both gated by the strict approved owner session:
- **Encrypted media export:** collects the complete private image index (no silent partial export), validates each generated private file, creates the encrypted companion in an internal temporary file, authenticates and decodes every staged image, writes to the user-selected document destination, then opens that destination again and authenticates it before reporting success. The passphrase is held only for the operation and character buffers are cleared.
- **Restore preview:** opens a user-selected bundle, prompts for its passphrase, decrypts into a new cache staging directory, verifies GCM authentication, manifest limits, lengths, hashes, image signatures and bounded Android decode, then displays bundle metadata and category counts. The staging directory is deleted when the preview is dismissed or any step fails.

Important current boundary:
- This UI does **not** apply restored images, change the live wardrobe index, replace existing files, or delete existing files.
- The next implementation stage must add a separate owner-confirmed staging/apply transaction with a pre-change media snapshot, index validation, safe promotion, read-back verification, rollback and crash recovery.
- Export and import actions have not been compiled or exercised on a device. No tests, Gradle build, APK build or Actions run has been performed.


## 21. Additive owner-confirmed restore transaction (2026-11-20)

Implemented on `ava/mobile-app-integration`:
- Added `AvaWardrobeMediaRestoreTransaction` and JVM tests.
- Restore is additive only. It never overwrites or deletes a current indexed image.
- Same item ID + same SHA-256 is treated as unchanged only after the existing path is verified as a safe private wardrobe file and its bytes are re-hashed.
- Item IDs are compared case-insensitively; each staged entry is rechecked for UUIDv4 identity, generated filename, private staging location, category, byte limit, MIME/signature agreement, digest and bundle aggregate size before promotion.
- The copy/promote/index operation runs off the Android UI thread; the owner confirmation and final status remain in the UI.
- Same item ID + different digest is a hard conflict. A filename collision outside the current index also stops the complete operation.
- Existing index records must have completed migration metadata (stable item ID and SHA-256); malformed or incomplete indexes fail closed.
- All incoming files are copied to a new transaction staging directory, synced, and re-hashed before a recovery journal is written.
- The journal records old/new index digests and transaction-generated filenames paired with each expected SHA-256. It does not store display names, image content, passphrases, or device paths.
- Recovery verifies each promoted file against its expected digest and the corresponding index record before accepting a committed transaction. During rollback, it deletes only files whose digest matches the transaction record; unexpected files are preserved and recovery fails closed.
- The encrypted index is checked before journaling and again immediately before commit to avoid overwriting a newer concurrent wardrobe change.
- Staging cleanup and the recovery-completion audit must succeed before the journal is removed. If audit or cleanup fails, the journal remains available for retry.
- Staged files are promoted using generated names that cannot collide with an indexed file. Only after promotion does the code commit the encrypted SharedPreferences index and read it back exactly.
- If the old index is still active after a failure, only files promoted by this transaction are removed. If the new index was committed but a later step failed, the journal and promoted files are retained for recovery.
- Owner authorization and required audit callbacks are checked before preparation, while staging/promoting, before index commit, and after commit.
- Recovery is owner-gated. It compares the current encrypted index digest with the journal's old/new digest: old means remove only transaction-owned files; new means verify every promoted file is present and indexed, then clean temporary state. An unknown digest or audit failure preserves the journal and fails closed.
- Since the algorithm is strictly additive and does not mutate existing media, a pre-change media bundle is not required for this merge mode. A future replace/overwrite mode would require a separate explicit choice and a verified pre-change backup; it is not part of this implementation.
- The owner preview dialog now offers a second explicit confirmation before additive application. The wardrobe page invokes journal recovery before legacy metadata migration.

Tests added:
- additive restore preserves old records and commits new media;
- failed index write rolls back only newly promoted media;
- same ID with different digest is rejected without changing current files;
- stale index changes abort before journal creation or live promotion;
- recovery refuses to delete a file whose digest does not match the transaction journal;
- recovery rolls back only files whose digest matches the journal when the old index is still active;
- a failed recovery-completion audit leaves the journal for a safe retry;
- a committed index with an incomplete final audit leaves a journal that recovery can safely finalize.

Verification boundary:
- Source, UI integration, tests and documentation have been written to the branch and are awaiting static read-back review.
- No Gradle tests, compilation, APK build, device test or GitHub Actions run has been performed. One final workflow run remains reserved until all implementation and static review work is complete.
