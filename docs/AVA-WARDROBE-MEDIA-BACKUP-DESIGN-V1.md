# AVA Wardrobe Media Backup Bundle
## Design and Safety Review v1.0
Status: DESIGN REVIEWED • IMPLEMENTATION PENDING
Branch: ava/mobile-app-integration
Scope: Owner-imported wardrobe and hairstyle image files only

## 1. Purpose
The current encrypted JSON backup deliberately excludes owner-imported wardrobe image bytes and the `owner_wardrobe_images` index. This document defines a separate encrypted media bundle so images can be exported and restored without expanding the existing JSON backup envelope or weakening its size limits.

This is a design specification, not an implemented feature. No image backup or restore capability is claimed until code, tests, and a device build verify it.

## 2. Current verified storage model
- Image bytes are copied into app-private `filesDir/ava-wardrobe`.
- Each imported image is limited to 15 MiB at import time.
- The encrypted `ava_ui` preferences contain the `owner_wardrobe_images` JSON index.
- Index records currently include name, category, categoryLabel, absolute file path, and addedAt.
- The UI validates that each canonical file path remains under the private wardrobe directory before displaying it.
- The normal portable backup exports memories, profiles, vocabulary, tasks, AACS portable state, and selected settings. It does not export the wardrobe index or image bytes.
- Restore replaces the supported data buckets and settings. It does not restore wardrobe media.
- The app currently displays a warning that imported photos are not included in encrypted backups.

## 3. Non-goals
- Do not put image bytes or Base64 image strings into the existing JSON payload.
- Do not change the existing backup-chain format, archive hashes, or legacy restore behavior.
- Do not export API keys, active sessions, private grants, access tokens, Keystore keys, or external-send consent.
- Do not expose private wardrobe media in public-only mode or another person's session.
- Do not fetch remote image URLs during restore.
- Do not delete the current wardrobe as a side effect of a failed import or failed restore.

## 4. Proposed bundle format
Use a separate versioned file, suggested extension `.avamed`, containing:
1. A small authenticated header with magic identifier, format version, KDF identifier and parameters, salt, nonce, and ciphertext length.
2. An encrypted archive payload. Prefer ZIP entries with STORED mode for already-compressed image formats (JPEG, PNG, WebP, GIF), avoiding pointless recompression.
3. A manifest inside the encrypted payload containing schemaVersion, bundleId, createdAt, item count, and one record per image.
4. Each image record contains a generated stable itemId, safe generated relative filename, display name, category key, category label, byte length, media type, and SHA-256 digest.
5. The bundle must not contain absolute paths, original content-provider URIs, or user credentials.

The encrypted payload must be authenticated with AES-256-GCM. Derive the key from the user-entered passphrase with the project's approved password KDF parameters and a fresh random salt. Use a fresh random nonce for every export. Authenticate the format header as AEAD associated data. Never reuse a nonce with the same key. Clear mutable passphrase/key buffers in finally blocks.

The media bundle is independent of the JSON backup and may be exported alongside it. The user must be told that both files are needed for a complete restore.

## 5. Resource bounds
Initial conservative limits:
- Maximum image count: 100.
- Maximum individual image size: 15 MiB.
- Maximum aggregate uncompressed image bytes: 250 MiB.
- Maximum manifest size: 1 MiB.
- Maximum archive entry count: image count plus the manifest only.
- Maximum filename length: 100 characters; generated filenames only.
- Reject path separators, dot-dot segments, absolute paths, duplicate item IDs, duplicate filenames, unsupported MIME types, and unexpected archive entries.
- Enforce limits while streaming, not only from declared lengths.
- Reject truncated streams, trailing unexpected payload, malformed manifest, and any digest mismatch.

These are application limits, not promises about the available free storage. Check available space before staging and fail without touching current data if space is insufficient.

## 6. Export flow
1. Require a valid approved owner session, owner access level, and non-public-only mode. Re-check authorization immediately before reading private media.
2. Read the encrypted preference index and validate every record.
3. Resolve each file canonically and require it to remain inside `filesDir/ava-wardrobe`; reject missing, symlink-escaped, unreadable, or oversized files.
4. Stream each image into a temporary archive while calculating SHA-256 and actual byte count.
5. Write the manifest using generated safe filenames, never stored absolute paths.
6. Encrypt the archive to a temporary destination using authenticated encryption.
7. Flush and close all streams, verify the finished bundle can be authenticated and its manifest/digests can be read, then publish the final destination.
8. Delete temporary plaintext archive material in finally blocks. Record owner audit events for export start, success, and failure without logging names, paths, image bytes, or passphrases.

A failed export must leave the existing wardrobe and existing backup files unchanged.

## 7. Restore flow
1. Require the same owner-only authorization and explicit confirmation that the media bundle will replace or merge with the local imported-image collection. Default behavior should be **replace only after preview and confirmation**; never silently merge duplicates.
2. Authenticate and decrypt the bundle into a private staging directory. Do not extract directly into the live wardrobe directory.
3. Validate header, schema, item count, category allowlist, safe generated filenames, MIME sniffing, declared and actual sizes, aggregate limits, and every SHA-256 digest.
4. Build a new index using generated local canonical paths. Do not trust or reuse any path from the bundle.
5. Verify that every staged file exists, is readable as an image, and remains within the staging root.
6. Write a restore journal recording operation ID, phase, and staging/previous directory names. The journal must not contain secrets.
7. Keep the current live directory and encrypted index intact until the staged collection is fully verified.
8. Commit by moving the old directory to a rollback name, moving staging into the live location, and committing the new encrypted preference index. Because filesystem rename and SharedPreferences commit are not one atomic transaction, use a recoverable journal:
   - If the new index commit succeeds, mark committed, then remove the old directory.
   - If index commit fails, restore the old directory and retain the old index.
   - On app startup, inspect an unfinished journal and deterministically roll back to the last fully committed state.
9. Re-check owner authorization before the final commit. If authorization has expired, abort and remove staging only.
10. Record audit outcome. Never log private image names or contents.

## 8. Data model migration
Current records store absolute paths and do not have a stable item ID. Before bundle support:
- Add a stable UUID itemId to each record.
- Keep category values restricted to sporty, everyday, formal, homewear, private, hairstyle, and other.
- Migrate old records in place without changing their current canonical file paths.
- Validate every migrated record and file before committing the new encrypted index.
- Preserve backward compatibility with records that lack itemId until migration succeeds.
- Do not infer an item's category or name from its image pixels.

## 9. Failure and recovery rules
- Wrong passphrase or authentication failure: no extraction, no writes to live storage.
- Invalid manifest, digest mismatch, archive traversal, duplicate identifiers, oversized file, or unsupported media: reject entire bundle; do not partially restore.
- Insufficient storage: abort before touching live data.
- Process death during staging: clean or resume staging safely on next launch.
- Process death during commit: use the journal to restore the last committed directory/index pair.
- Failed cleanup of rollback material: keep a visible recovery warning and do not overwrite it on a later restore.
- Never silently discard current local images.

## 10. Test plan required before implementation is considered complete
Pure policy/unit tests:
- Valid manifest and all supported categories.
- Invalid schema, unknown category, duplicate IDs, duplicate filenames.
- Path traversal and absolute path attempts.
- Per-file, aggregate, count, and manifest size limits.
- Digest mismatch, truncated file, altered ciphertext, wrong passphrase.
- Unsupported MIME type and MIME/content mismatch.
- Missing image and unreadable image.
- Legacy index migration and idempotent migration.
- Bundle with unexpected entries or trailing data.

Storage/integration tests:
- Export then restore round trip preserves image bytes, display names, categories, and order.
- Export failure leaves source files and index unchanged.
- Restore failure at every stage leaves old live directory and index intact.
- Simulated process death at each journal phase recovers to one consistent state.
- A failed SharedPreferences commit rolls back the directory swap.
- Owner session expiry and public-only mode block export and final restore commit.
- Non-owner session cannot read, export, or restore private media.
- Existing JSON backup/restore tests and backup-chain tests remain unchanged and pass.

Device acceptance:
- Test on Android 13 / Redmi Note 11 class hardware.
- Confirm streaming behavior and memory use; do not load all images into memory.
- Confirm private media remains app-internal and is not visible to other apps.
- Confirm uninstall/data-clear warning remains accurate until both backup and restore are shipped.

## 11. Implementation sequence
1. Add pure manifest/entry validation policy and unit tests.
2. Add legacy index migration to stable IDs and test idempotence.
3. Implement bounded streaming archive writer/reader and cryptographic envelope.
4. Implement staging verifier and journaled directory/index commit.
5. Add owner-only UI for export and restore, including preview, replace confirmation, and clear distinction from the JSON backup.
6. Add failure-injection and integration tests.
7. Perform full code review and run the existing test suite.
8. Only after all code and tests are ready, run the single planned GitHub Actions build. Do not merge or modify master as part of this work.

## 12. Current status
- Design: documented.
- Existing JSON backup behavior: reviewed; wardrobe media excluded.
- Stable image IDs: not implemented.
- Media bundle encryption: not implemented.
- Export/restore UI: not implemented.
- Staging/journal recovery: not implemented.
- Tests: not implemented.
- Build/Actions: intentionally not run.
