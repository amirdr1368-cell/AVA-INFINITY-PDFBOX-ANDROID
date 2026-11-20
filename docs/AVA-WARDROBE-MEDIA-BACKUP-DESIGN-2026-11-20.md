# AVA INFINITY - Wardrobe Media Backup Design

Status: DESIGN PROPOSAL ONLY  
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

1. Extract and test one shared strict owner-session authorization helper for wardrobe media operations, without changing the behavior of existing backup/export controls.
2. Define a versioned index migration that adds stable item IDs and verified media metadata while preserving old entries and their files.
3. Harden image import to validate actual file signatures and bounded dimensions before the index commit.
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
- Header detection plus Android bounds decoding is an import hardening step, not a complete hostile-file decoder sandbox. The portable media backup/restore feature remains unimplemented.


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
