# AVA INFINITY Mobile Implementation Status

Updated: 2026-11-17

## Implemented on `design/ui-2.0-glass-profile`

- UI 2.0 foundation: Sultan-lavender / soft-pink / rose-gold palette, glass-style panels and chat bubbles, plus a fourth contrast-aware light palette.
- AVA profile image selection, persistent URI, reset-to-default, visibility, display size, typography scale and glass-transparency controls.
- Encrypted app-local preference stores using AES-GCM with Android Keystore-held keys; legacy plaintext values migrate on first read. Android Auto Backup is disabled for app-private state.
- PRP persistent profiles for Levels 2 and 3 only; Level 4 remains non-persistent by design. Arash Naderi is seeded as Level 3, with high qualitative closeness and no numeric intimacy score.
- PRP profile fields include interests, preferences, privacy restrictions, relationship history and information provenance.
- Photo selection is gated by recorded receipt of the person's consent. Relationship history and per-person memory require owner approval. Consent decisions are written to the AACS audit trail.
- Separate owner and per-person memory buckets. When relationship-memory approval is absent or revoked, the active bucket is moved into an encrypted quarantine and remains inaccessible until the owner re-approves. Portable migration preserves quarantined memories and relationship-history text without reactivating consent; seven-day temporary items are still filtered and cleaned.
- MEM-02: temporary memories expire seven days from intake; viewing does not reset expiry; extensions require owner approval and a reason. Expired temporary items are hidden immediately even if physical cleanup is temporarily blocked; cleanup retries on app resume. Temporary memories are excluded from new backup archives.
- AACS foundation: session lifecycle, Android device-credential owner approval, persistent public-only mode, ephemeral Level 4 guest sessions, per-person data-class permissions with expiry, page-level restrictions, session-history clearing and encrypted audit events.
- Public-only mode overrides even owner-level shortcuts for non-public data, blocks external AI transmission and microphone/TTS output, stops ongoing speech, clears the current conversation history, and can only be exited after fresh Android owner authentication.
- Personal data requires an owner-approved session for the exact person. Private-data grants are one-time and short-lived; persistent per-person permissions cannot unlock private or control scopes. Permission checks fail closed if encrypted permission data cannot be read.
- Owner-only security screen shows recent AACS audit events. The central console shows live session, privacy, vault, backup and AI-provider status with shortcuts to core modules. Audit records are encrypted at rest and writes are synchronous; a corrupt audit trail is not silently reset.
- AVA SECRET vocabulary supports categories, editing, version history and deletion.
- Daily assistant supports tasks, descriptions, priorities, due dates, completion and deletion.
- Personality controls: preferred tone, response length and emoji use, versioned history (30 versions) and owner-confirmed rollback with AACS audit. Voice controls: Persian speech recognition, selectable installed Persian TTS voices, speech rate and pitch, plus optional automatic spoken replies. Speech-provider privacy is disclosed; microphone recognition and TTS playback are disabled in public-only mode.
- Conversation engine abstraction: local fallback plus optional OpenAI-compatible Chat Completions adapter. External sending is disabled by default; enabling it requires explicit consent. Endpoint/model/API key settings are stored in encrypted preferences, HTTPS is required, and the UI discloses possible provider costs.
- AEMS primary vault foundation: user-selected removable-storage folder, per-bucket AES-GCM encryption with a device-bound Android Keystore key that never leaves the device, verified temporary-write/replace flow and retained last-known-good previous object.
- Vault operations are serialized across app and worker instances. Object and recovery-queue size limits are enforced. If the selected vault becomes unavailable, new bucket snapshots are held in an encrypted bounded recovery queue (up to 100 buckets, 10 MB per bucket and 50 MB total); app resume attempts synchronization.
- Primary vault setup validates a mounted removable storage volume. Automatic backup setup rejects the same local storage volume as the primary vault, not merely the exact same folder.
- Weekly vault integrity worker verifies encrypted bucket read-back and records the result.
- Manual portable encrypted backup/export/import using PBKDF2-HMAC-SHA256 (210,000 iterations) and AES-256-GCM; backup input/output sizes are bounded and derived/plain byte arrays are cleared after use. Portable packages include personality history, voice preferences, UI settings, safe public/personal permission overrides and historical AACS audit events. API keys, external-send consent, active sessions, private grants, device-bound keys and device-specific avatar URIs are excluded.
- Optional automatic backup to a separate user-selected SAF folder: daily snapshots, weekly/monthly snapshots, change-triggered event snapshots, retention limits (30 daily / 12 weekly / 12 monthly), encrypted read-back verification and status reporting.
- Restore flow previews date, size and authenticated integrity; requires Android owner re-authentication and a separate final replacement confirmation. Restored buckets and profile IDs are read back and verified before success is recorded. When an automatic destination is configured, a verified pre-restore milestone snapshot is required before replacement. Restore decisions and outcomes are audited.
- AEMS-06 Android migration flow: portable backup is imported and read-back verified on the new phone first; only then can the owner authenticate and explicitly confirm removal of old device-bound AVA vault objects from the moved card. The new phone creates a fresh Android Keystore key, re-encrypts restored data to the card, verifies it, and clears the migration-ready marker only after success. The old phone is to be retained until verification completes. Historical audit and eligible access overrides are migrated; active approvals and private grants are not. External AI opt-in and API credentials must be re-entered on the new device.

## Explicit limitations

- Android CI has repeatedly failed for recent PR heads. The latest inspected run for HEAD `f615a3fc72b2568cd56d730b4a9e5aabec46e5b6` completed within seconds with no job steps or downloadable log artifact; GitHub returned a missing log blob. The failure cause is unknown. No successful build is claimed.
- The conversation adapter is not configured with a provider or API key by default. It has no streaming output; local mode is intentionally limited and does not pretend to be a full language model.
- AACS is not yet enforced across every possible sensitive operation. Level 4 is manually initiated and public-only; automatic environment/overhearing detection is not implemented. User must activate privacy mode when a conversation may be overheard.
- Internal encrypted cache still duplicates some data; the storage lifecycle is not yet reduced to the intended minimal boot-critical/index cache. microSD capacity/health beyond read/write integrity is not monitored.
- Change-triggered backup jobs currently create verified full snapshots, not true delta/incremental archives. Optional second cloud destination, isolated restore testing, fully transactional rollback and cross-platform key migration remain pending.
- Temporary data is excluded from new backup archives, but older legacy backups created before this rule cannot be selectively scrubbed. Expired data is removed when such a backup is restored and startup/resume cleanup runs.
- Prohibited-content classification is not automated; the UI warns and requires an owner attestation, but this is not a complete classifier.
- Wake phrase and dedicated custom AVA voice are not implemented. The light theme is implemented as a fourth palette; contrast review and remaining personality, communications and app-control refinements remain pending.
- The requested final full audit, security review and bug review are deliberately deferred until the implementation scope is complete.

## Next implementation sequence

1. Complete AACS enforcement across every sensitive operation, including remaining Level 4 and privacy edge cases.
2. Implement true delta backups, optional independent second destination, isolated restore tests, transactional rollback and cross-platform migration.
3. Complete voice behavior, wake-phrase design and remaining personality / communication / control-center modules.
4. Review light-theme contrast across all screens and finish remaining UI customization.
5. Review microSD cache minimization, capacity monitoring and card-failure recovery.
6. Resolve CI failure and verify a successful build on the exact latest HEAD, then run the requested full audit and bug/security review. Keep the PR in draft until verified.


## Core completion work — 2026-11-17 (no Build requested)

### Completed in `implementation/core-completion`
- AVA SECRET entries created from the UI are drafts by default and are not treated as published vocabulary.
- Legacy entries without governance metadata receive stable IDs and `legacy_review_required` status during an owner-context migration; legacy content is preserved for review.
- Vocabulary records now support language code, a map of multilingual equivalents, optional pronunciation, status, version and history snapshots.
- Editing a vocabulary entry archives the prior state and returns the changed entry to draft status; it must be approved again before publication.
- Publication, rollback and archival require Android device-credential confirmation followed by a short-lived, single-use AACS owner-action grant.
- Rollback creates a new draft version from a selected historical snapshot. Archival is a tombstone/status transition and does not physically delete the entry or its history.
- AVA SECRET management is blocked in public-only mode and during non-owner sessions.
- Pure governance rules were extracted into `AvaVocabularyPolicy`; JUnit tests were added for language-code validation, owner-context rules, publishable states and version increments. Tests have not been run.
- External conversation-provider instructions now explicitly cover Persian default/language switching, balanced respectful tone, non-diagnostic emotional support, uncertainty disclosure, privacy boundaries and non-intrusive topic resumption. These are provider prompt policies, not proof of local model behavior or a complete ACL policy engine.
- Event-triggered backup work now uses WorkManager `APPEND_OR_REPLACE` so a new event does not cancel an in-flight event backup.
- Removed four misplaced duplicate files under `app/app/src/main` and nested package paths from this implementation branch. The valid Android source remains under `app/src/main`.

### Still incomplete / not verified
- ACL-01 through ACL-06 are only partially represented in the external-provider system prompt. Language detection/state, profile-based session adaptation, explicit confidence orchestration, adaptive emotional support and proactive unfinished-topic suggestions need implementation and tests.
- ACL-07 intentional voice interruption, reliable speaker discrimination, latency measurement and wake phrase remain pending.
- ACL-08 now has the core draft/approval/version/rollback/archive flow. The current UI exposes English equivalent and pronunciation fields; a full multilingual editor, duplicate/conflict detection, richer pronunciation metadata and acceptance tests remain pending.
- AACS enforcement across every sensitive operation and full edge-case audit remain pending.
- AEMS true delta archives, isolated restore tests, fully transactional rollback, second independent/cloud destination, removable-media capacity/health monitoring and cache minimization remain pending.
- No Build, APK generation, CI rerun, or physical-device test was performed in accordance with Amir's instruction. Static source inspection is not runtime verification.


## AACS and privacy enforcement follow-up — 2026-11-17

Additional source changes committed after the AVA SECRET work:
- Local memory bucket reads/writes are now checked against the active AACS session. Non-owner sessions can access only their own approved relationship-memory bucket; owner-only buckets (Amir memories, tasks, AVA SECRET, quarantine and backup/export/restore paths) are blocked for guest sessions.
- Profile reads are scoped to the current session person; public-only mode returns no profile list. Profile create/edit/delete/export/restore operations require owner context.
- AACS session start rejects invalid identity/level combinations and overlapping sessions. Owner session approval now requires a short-lived grant after Android device-credential success.
- Private information now requires a separate Android device-credential confirmation after the session is approved; the resulting grant is single-use and the access window is bounded to 60 seconds.
- Public-only mode exit now requires a separate short-lived device-authenticated grant. The security screen remains reachable for session termination while hiding owner-only audit and permission controls from non-owner sessions.
- Automatic backups pause during active sessions and public-only mode so guest/session content is not silently included.
- External AI transmission is disabled during non-owner sessions, even if the owner's global provider setting is enabled.
- Event-triggered backups use WorkManager append semantics rather than replacing an in-flight event job.
- Added per-language normalized duplicate/conflict checks for vocabulary terms and equivalents.

### Verification limits
- A lightweight source scan found balanced delimiters in the inspected Kotlin files and no stale `english.text` or old `allowPrivateInformation(true)` call sites.
- This scan is not a Kotlin compiler. JUnit test sources have been added but not executed.
- Build, APK, CI, and physical-device testing remain intentionally unperformed under the no-Build instruction.


## ACL-01 language behavior — 2026-11-17

- Added `AvaLanguagePolicy`: Persian default, explicit language-change phrase recognition, script-based language detection for common scripts, and basic common-language detection for Latin-script messages.
- Selected response language is stored in encrypted app preferences and reused until a language change is detected.
- External-provider system context now names the active response language rather than assuming Persian for every turn.
- Local fallback supports Persian and English canned responses. For other selected languages, it transparently reports that local generation is limited; full multilingual generation still requires an explicitly configured external provider.
- JUnit tests cover Persian default, English/Persian switching, explicit French/Arabic selection, ambiguous Persian greeting, Arabic text, and common non-Latin scripts. Tests are not run.
 
## Final core-stage source audit additions — 2026-11-17

- AACS now requires separate owner device-credential confirmation for session approval, one-time private-data access, and exit from public-only mode. Each uses a short-lived grant; stale grants are cleared before new authentication and on session transitions.
- Restricted users can always reach the security screen to end their session, even if their ordinary public data scope is denied. Owner-only audit and permission controls remain hidden.
- The conversation language policy is persisted in encrypted preferences; provider prompts receive the selected language. Local mode supports canned Persian and English only.
- Vocabulary equivalent editing is language-code agnostic; adding/updating a translation preserves other language entries. Duplicate detection is normalized and scoped per language.
- Deferred event backup is re-enqueued after the owner ends a restricted session and exits public-only mode.
- The source-level scan was repeated after these changes. No stale references to the old private-access API or removed English-only editor variable were found. No Build or test execution occurred.


## ACL-02 to ACL-04 implementation progress - 2026-11-17

- ACL-02: tone settings now map to bounded instructions used by local replies and provider prompts. The lively tone is constrained for sensitive or distress-related subjects. Response length and emoji preferences are applied.
- ACL-03: added stateless current-turn support classification for explicit listening, suggestions, lightening the mood, and space requests, plus tentative distress and clarification modes. No inferred emotion is written to profiles, memories, or audit records.
- ACL-04: added session-scoped audience adaptation. The conversation engine only reads the exact active Level 2/3 profile after AACS confirms PERSONAL access for the approved session. Public-only, owner, public guest, unapproved, and mismatched contexts do not receive profile adaptation.
- ACL-04 maps only allowlisted signals from speakingStyle and preferences into bounded style, response-length, and emoji choices. It does not forward raw profile text, relationship history, intimacy, occupation, interests, or privacy restrictions as model instructions.
- A lasting preference stated during a person session produces a non-persistent proposal response for owner review. No profile write or automatic preference update occurs. Audience context is re-derived per request and not persisted.
- Added AvaAudiencePolicyTest.kt for exact profile/session matching, access-level boundaries, public-only behavior, prompt-injection-resistant allowlisting, informal-style classification, and non-persistent preference proposals. Test source is not executed.
- ACL-02/03/04 remain partial pending full acceptance and integration review. ACL-05/06/07 and AEMS gaps remain open.

### Verification boundary
- Delimiter/reference checks were performed on the newly changed policy and conversation files.
- This is not compiler verification. No Gradle Build, JUnit execution, APK generation, CI run, or physical-device test was performed.


## ACL-05 confidence handling - 2026-11-17

- Added AvaConfidencePolicy and connected it to local fallback replies and external-provider system guidance.
- Clear requests are directed to direct answers. Narrow consequential ambiguous references require clarification when no recent assistant context exists; with recent context the likely interpretation must be stated tentatively and correction invited before consequential action.
- Explicit correction phrases trigger acknowledgement and answer revision. Explicit humor/irony phrases are treated cautiously; laughter markers alone do not override possible distress.
- No numeric confidence percentages are produced. The policy uses qualitative handling modes only.
- Added AvaConfidencePolicyTest.kt covering direct requests, ambiguous references, recent context, corrections, humor, and laughter-only input. Test source is not executed.
- Remaining limitation: deterministic phrase heuristics are not a calibrated confidence model and require broader acceptance/integration review. ACL-06 and ACL-07 remain open.

### Verification boundary
- Delimiter and integration-reference scans passed for the newly changed policy, conversation engine, and test sources.
- This is not compiler verification. No Gradle Build, JUnit execution, APK, CI, or device test was performed.


## ACL-06 unfinished-topic follow-up - 2026-11-17

- Added AvaFollowUpController, an Activity-lifetime in-memory controller with no disk persistence or scheduling API.
- A user deferral can capture the immediately preceding user topic, then offer one in-chat suggestion when chat is reopened or the user explicitly signals continuation.
- Explicit refusal/disinterest or completion clears the topic. No repeated suggestion is emitted after it has been shown.
- State is owner-context only and is cleared for non-owner/public-only contexts, on session/privacy transitions, and on Activity destruction.
- No out-of-app notification or scheduled reminder is implemented. Future notification scheduling remains gated on separate explicit consent; private topic text is not sent to notification surfaces.
- No topic is written to temporary memory or profile storage. Durable project decisions continue to live in project documentation/project memory.
- Added AvaFollowUpControllerTest.kt for deferral, single suggestion, continuation, refusal, completion, missing prior topic, and session/privacy boundary behavior. Test source is not executed.
- ACL-06 remains partial pending UI acceptance and broader integration review.

### Verification boundary
- Static delimiter and call-site checks were performed for the controller, MainActivity integration, and tests.
- This is not compiler or runtime verification. No Build, JUnit execution, APK, CI, or physical-device test was performed.


## ACL-07 voice interruption foundation - 2026-11-17

- Added explicit voice state tracking for idle, listening, processing, speaking, and error; the state is shown in chat, voice settings, and the central console.
- Added user-controlled immediate stop controls in chat and voice settings. The stop action stops TTS and cancels/destroys the current speech recognizer without clearing conversation history.
- Explicit microphone start stops TTS before starting foreground speech recognition. Recognized speech is inserted into the composer for owner review and is not automatically sent.
- TTS progress callbacks update speaking/idle/error states. Recognition errors, unavailable services, recognizer creation/start failures, and microphone permission denial are surfaced.
- No always-on listening is enabled.
- Automatic speech interruption is intentionally not active because the current SpeechRecognizer/TTS setup does not reliably distinguish Amir's voice from ambient speech or the speaker's own TTS echo. A false positive would interrupt without valid evidence.
- The <100 ms manual-stop and <300 ms valid-speech interruption values remain targets only. They are not measured; P95, speaker discrimination, and noise testing remain pending.
- ACL-07 remains partial pending a reliable speaker-verification/echo-handling design and acceptance review.

### Verification boundary
- Source delimiter and integration-reference checks only. No Build, tests, CI, APK, or device testing was performed.


## AEMS-07 backup and restore hardening - 2026-11-17

- AEMS-07.1: the configured backup destination remains separate from the primary microSD volume; the portable archive uses a passphrase-derived AES-GCM key and excludes Android Keystore/device-bound keys. One destination is implemented; a second independent optional cloud copy is not yet implemented as a separate replica.
- AEMS-07.2: the 24-hour full backup remains scheduled. Event-triggered backup jobs remain queued through WorkManager and now validate the current payload schema and authenticated read-back before promotion. Important-change jobs are still full snapshots, not true delta archives; incremental/delta chains remain open.
- AEMS-07.3: 30 daily, 12 weekly, and 12 monthly versions are retained. Pre-change milestone files are stored under a separate prefix and are not pruned by ordinary retention. Pruning occurs only after a new backup has been authenticated and schema-validated. Failed writes do not trigger cleanup of healthy versions.
- AEMS-07.4: added weekly independent-destination health checks for the latest available daily/weekly/monthly/event/milestone representative files, and a monthly isolated in-memory staging test for the latest daily backup. The staging test decrypts, validates, clones and compares the payload without writing to live stores. It is not yet a full restore into a separate temporary storage adapter.
- AEMS-07.5: destination unavailability and backup failures update the in-app status and AACS audit trail; WorkManager retains/retries queued backup work. The worker pauses if the primary vault is unsafe, validates existing healthy copies and the new payload, and does not delete old healthy versions to make room. After reconnect, health checks and authenticated read-back gate the next verified archive.
- AEMS-07.6: restore now requires an independent pre-restore milestone. A trusted in-memory snapshot of buckets, profiles, settings and AACS portable permissions is captured before mutation. If restore fails after mutation begins, the previous state is restored and read-back verified; exact AACS permission rollback preserves the current audit trail. If rollback or its audit fails, Ava stops voice output and enters public-only mode. Restore preview, owner device re-authentication, explicit final confirmation, and success/failure audit remain in place.
- Major relationship-memory approval and AVA SECRET archive/rollback actions now create pre-change milestone snapshots. Privacy revocation remains available and is not blocked by backup availability.

### Remaining AEMS work
- True delta/incremental backup files and chain-aware point-in-time reconstruction.
- A separately configured optional cloud replica in addition to the primary independent destination.
- Full isolated restore through a temporary storage adapter, including fault injection and transaction/rollback acceptance tests.
- Broader health coverage across every retained archive, media-failure/no-space scenarios, and measured operational acceptance.
- No Build, tests, CI, APK, or physical-device verification was performed. Source workers and JUnit test sources have not been executed.


## AEMS-07 chain redesign v2 - 2026-11-17

- Added the engineering redesign document docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md and linked it from README.
- Proposed archive model: independent daily/weekly/monthly/milestone full archives plus event deltas linked by base full ID, parent archive ID, strict sequence, payload hash, and chain hash.
- First delta format is intentionally section/bucket-level replacement with explicit tombstones, not item-level operations, because the current data model does not provide one stable immutable ID contract across every exported record.
- Proposed replay verifies the base and every parent in order; a missing or invalid chain member stops recovery at the last verified point.
- Defined dependency-aware delta retention, one serialized queue, two-phase archive/catalog commit, destination failure handling, and isolated transactional restore gates.
- Added a source guard so ordinary backup jobs reject a backup destination on the same detected physical volume as the primary vault and require read/write access.
- Important distinction: event-triggered source backups are still full snapshots. Delta creation/replay, encrypted catalog, queue unification, dependency-aware pruning, isolated storage-adapter restore, and cloud replication are not implemented yet.
- The 100-delta and 25%-of-base-size force-full thresholds are engineering proposals, not approved measured limits.
- No Build, tests, CI, APK, or device verification was performed.


## AEMS chain v3 reconciliation - 2026-11-17

- The canonical design document is docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md. The temporary duplicate design note was removed and README now links only to the canonical document.
- The canonical design was reconciled with the approved AEMS-07 daily full requirement: daily full baseline, event deltas between daily fulls, additional weekly/monthly full checkpoints, and independent milestone full archives.
- AvaBackupChain source helper is now schema v3 with sequence continuity, previous-chain digest, deterministic chain-header digest, parent-payload verification, and fork/cycle/missing-parent rejection.
- The helper remains unintegrated with AvaBackupWorker and AvaEncryptedBackup; event jobs still write format-1 full snapshots. No runtime chain migration is claimed.
- No Build, tests, CI, APK, or device verification was performed.


## ACL-05 and AACS edge hardening - 2026-11-17

- ACL-05 recent context now requires the immediately preceding history item to be an assistant message.
- Ambiguous high-impact references for delete/remove/send/change now require exact-target clarification and explicit confirmation; provider guidance prohibits execution claims or transmission before confirmation.
- AACS session identity validation now rejects invalid levels and mismatched person/guest ID shapes instead of coercing out-of-range values.
- Even a Level 1 owner session is no longer implicitly approved by its level; it requires the explicit device-credential approval path.
- Access-downgrade handling accepts only valid profile IDs and valid downgrade directions.
- AACS rollback verification now checks the exact restored permission-key set, not only the entries that were expected to exist.
- Added pure AACS identity/downgrade policy tests and ACL-05 source tests. Not executed.
- No Build, tests, CI, APK, or device verification was performed.


## AEMS backup-chain v2 implementation update - 2026-11-17

- Added the v2 design baseline in docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md. It defines full baselines, parent-linked deltas, authenticated chain reconstruction, manifest handling, dependency-aware retention, legacy compatibility and fail-closed restore.
- Added AvaBackupChain with immutable archive IDs, deterministic canonical SHA-256 digests, full and delta envelopes, parent/base checks, cycle/fork/missing-parent rejection and reconstruction.
- Daily and event workers now create deltas from the verified current head. Weekly/monthly due checks create full baselines; the first v2 run creates an initial full baseline. If existing v2 archives cannot be read or the graph is ambiguous, automatic writes and pruning stop rather than silently re-rooting with a possibly wrong passphrase.
- Added AvaBackupChainRepository to authenticate and scan v2 files, reconstruct restore points, write an encrypted rebuildable chain index and prune only after dependency analysis. The index is currently written and verified but not yet used as a scan accelerator; the repository still authenticates archive files directly on each scan.
- Restore now offers folder-based chain-point selection plus single-file full-snapshot import. Delta files cannot be restored alone. Legacy v1 snapshots remain selectable and readable.
- Manual exports and pre-change/pre-restore milestones now use v2 full archives. The current chain uses section-level delta replacement; changed top-level sections are replaced as a unit. Record-level delta compression is not implemented.
- Added a shared snapshot lock around local bucket writes, profile reads/writes, portable settings writes and backup capture to reduce cross-section inconsistency.
- Updated weekly health verification to reconstruct chain points and check legacy snapshots. Monthly restore verification now stages the reconstructed chain head in memory without touching live stores.
- Added JUnit source tests for full/delta round-trip, multi-delta reconstruction, wrong parent digest, tampered result digest, fork, missing parent and input immutability. Tests are not executed.

### Remaining AEMS work
- Read/use the encrypted manifest as a verified cache and rebuild it explicitly when missing/stale; current scans still read all chain archives.
- Record-level deltas for large arrays, rather than section-level replacement.
- More exhaustive retention/fault-injection tests, including partial delete, low storage, media removal and interrupted manifest promotion.
- Full isolated persistence-adapter restore tests and rollback fault injection.
- Confirm the physical backup destination is independently mounted on the target device and validate real storage behavior.
- No Build, tests, CI, APK, or physical-device verification was performed.


## AEMS-07 chain integration correction - 2026-11-17

The v3 chain is now integrated in source, not only represented by a pure helper:
- Daily WorkManager runs create full snapshots; event runs create section-level deltas, and force a new full when the baseline is absent or at least 24 hours old.
- Initial, daily, weekly, monthly, and milestone full checkpoints are labeled for dependency-aware retention.
- Empty event deltas are skipped; archive timestamps are monotonic relative to the latest verified chain head.
- The repository scans/authenticates archives, reconstructs restore points, detects broken/forked chains, writes an encrypted rebuildable index, and prunes only after verified publication.
- Retention keeps 30 daily fulls, 12 weekly, 12 monthly, milestone fulls indefinitely, and delta ancestors required by retained restore points.
- Restore UI presents reconstructed v3 points alongside legacy v1 full archives and reports total chain size.
- Health and monthly staging workers now operate on the v3 chain; health also checks legacy v1 files. Both verify physical destination independence.
- Existing v1 archives remain independently restorable and are not silently rewritten.

Still open: index is written but not used as a fast-path; event queue has serialization but no dedicated debounce worker; proposed 100-delta/25%-size thresholds are not implemented; full isolated persistence-adapter restore with fault injection and optional cloud replica remain pending. No Build, tests, CI, APK, or device verification was performed.


## AEMS chain v3 integration correction - 2026-11-17

This section supersedes the earlier v2 implementation note above. The active source is now chain schema v3, not v2:
- Daily backup is a complete full snapshot. Event changes between daily full snapshots are linked deltas. Weekly/monthly retention labels are attached to due full snapshots; milestone archives are full snapshots.
- Chain v3 includes sequence continuity, previous-chain hash and deterministic chain-header hash in addition to payload digests. Delta creation verifies that the supplied parent payload matches the parent archive.
- The encrypted worker, repository, manifest writer, restore-point folder selector, manual export, milestone export, weekly health worker and monthly in-memory restore staging are now wired to v3.
- The worker refuses to create a new baseline or prune if an existing v3 archive is unreadable or ambiguous. This avoids treating a wrong passphrase as a recoverable chain break.
- Retention currently keeps 30 daily full labels, 12 weekly labels, 12 monthly labels, recent event deltas and all required ancestors; milestones are not pruned.
- The encrypted manifest is written and authenticated but is not yet used as a scan cache. Archive files remain the source of truth.
- Full restore-point reconstruction is wired. A delta file alone is rejected; folder import selects a reconstructed target. Legacy v1 full snapshots remain supported.
- Delta encoding is section-level replacement, not record-level. Restore staging is in-memory JSON, not a separate persistence adapter.
- JUnit source tests are present but not executed. No Build, tests, CI, APK or device verification was performed.
- Canonical design: docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md.


## ACL-07 voice lifecycle correction - 2026-11-17

- Added utterance-ID guards so stale TTS callbacks cannot change the current voice state after stop/replacement.
- Added recognizer-instance and generation guards so callbacks from a cancelled/replaced speech-recognition session are ignored.
- Terminal recognizer results/errors now clear and destroy the active recognizer.
- Explicit mic start still interrupts TTS and stages text without auto-submitting. Automatic speaker-verified interruption remains deferred.
- No Build, tests, CI, APK, P95 measurement, noise test, or device verification was performed.


## AEMS delta-chain threshold hardening - 2026-11-17

- Removed the volatile payload creation timestamp from newly captured portable payloads. Archive time remains in the authenticated outer archive envelope, allowing identical data states to produce empty deltas.
- Event delta chains now force a new daily full when the current baseline is 24 hours old, when the chain reaches 100 event deltas, or when cumulative event-delta file size reaches 25% of the baseline full-file size.
- Empty event deltas are skipped. Event jobs remain serialized with APPEND_OR_REPLACE to avoid losing changes; a dedicated safe debounce/coalescing mechanism remains open because a naïve KEEP policy can miss writes that occur while a worker is running.
- No Build, tests, CI, APK or device verification was performed.


## Independent backup destination policy - 2026-11-17

- Local SAF backup folders are accepted only when StorageManager confirms a mounted removable volume distinct from the primary vault volume.
- Phone internal shared storage and a second folder on the same microSD are rejected.
- A narrow allowlist of recognized remote SAF providers is accepted; unknown providers are not presumed to be remote.
- The policy is applied at folder setup, automatic backup, health checks, restore-staging checks, manual export, and pre-change milestone creation.
- Added pure policy tests; not executed.


## AEMS force-full thresholds - 2026-11-17

- Automatic backup now forces a full snapshot at 100 deltas per base or when cumulative delta file size reaches 25% of the base full, as well as at the 24-hour baseline limit and every daily run.
- These are initial source guardrails, not measured performance thresholds. No benchmark or runtime test was performed.
- Repository scans are serialized with publication and retention operations.


## AEMS manifest recovery and checkpoint policy update - 2026-11-17

- The repository now reads the latest encrypted index, validates its inventory/head metadata against the authenticated archive scan, and rebuilds a missing, stale or invalid index.
- The index is still not used to skip decrypting retained archives; a verified fast-path remains open.
- Extracted full-checkpoint policy into AvaBackupChainPolicy with source tests: create a full when the baseline is missing/24 hours old, event deltas reach 100, or cumulative delta bytes reach 25% of the baseline archive.
- The event queue remains serialized APPEND_OR_REPLACE. Safe debounce/coalescing remains open.
- No Build, tests, CI, APK or device verification was performed.


## AACS and backup destination fail-closed correction - 2026-11-17

- If an access downgrade cannot verify permission revocation or its audit event, AACS now enters public-only mode and blocks the profile-level change from being treated as complete. This prevents a later session from inheriting stale grants.
- A known cloud provider is not considered independent when the primary vault uses the same provider authority. Backup setup and worker execution now reject same-provider cloud destinations; a different known provider or a separate mounted removable volume may qualify.
- These are source-level hardening changes only; no Build, tests, CI or device verification was performed.


## AACS pre-approval profile exposure hardening - 2026-11-17

- A source audit found that PersonProfileStore could return a session member's profile before the AACS session had been owner-approved; an owner-level session could also expose all profiles before its approval step.
- Added an explicit AACS current-session approval query and a pure profile-visibility policy. Profiles are now hidden in public-only mode, before session approval, and from Level 4 guest sessions; approved Level 2/3 sessions see only their own profile; approved owner sessions and owner context retain owner access.
- Profile management, profile-cache refresh, AVA SECRET management, AACS permission mutation, portable AACS export/import and rollback now require an approved owner session when a session is active.
- Added JUnit source coverage for profile visibility and unapproved owner-session restrictions. Tests are not executed.


## AACS external-transmission epoch guard - 2026-11-17

- External AI transmission now requires an approved owner context, including an approved Level 1 owner session when one is active. An unapproved owner session no longer qualifies.
- AACS exposes a monotonically increasing in-process session epoch. Session start/end, approval, downgrade and public-only transitions invalidate pending transmission attempts.
- AvaConversationEngine checks the epoch and owner authorization before sending request bytes and again before delivering a remote reply. A session change cancels the pending send/reply path.
- Added pure-policy JUnit cases for owner approval, guest denial, public-only denial and stale session epochs. Not executed.


## AEMS restore transaction serialization - 2026-11-17

- The complete restore mutation/verification/rollback path now holds the shared chain lock and snapshot lock. Backup workers cannot capture an intermediate state while restore is replacing buckets, profiles, settings and AACS portable permissions.
- Lock order is consistent: chain lock before snapshot lock for worker, milestone and restore paths. Local profile/bucket/settings writes use the snapshot lock.
- No Build, tests, CI, APK or physical-device verification was performed.


## AEMS portable audit stability - 2026-11-17

- Backup-maintenance events remain in the device-local AACS audit log but are excluded from portable AACS snapshots. Security, permission and restore events remain included.
- This prevents each backup's own success/no-op event from changing the next snapshot and generating avoidable event deltas.
- Added a pure audit inclusion policy and JUnit source tests. Not executed.


## AEMS restore read-back verification hardening - 2026-11-17

- Profile restore and rollback now compare the complete serialized profile contents after writing, not only profile IDs.
- Portable AACS restore now verifies the audit-history write and every newly imported permission record by reading them back. Existing local permission decisions remain authoritative and are not overwritten.
- Together with bucket read-back and settings read-back, the restore path now checks all portable state categories before reporting success.
- No Build, tests, CI, APK or device verification was performed.


## AACS guest-profile minimization - 2026-11-17

- Approved Level 2/3 sessions now receive a minimized copy of only their matching profile: pending relationship history, unapproved relationship history, owner-only intimacy rating and privacy notes are hidden; photo URI is exposed only with photo consent.
- Audience adaptation continues to consume only allowlisted speaking-style/preferences fields.


## AACS profile minimization policy extraction - 2026-11-17

- Extracted guest profile filtering/sanitization into AacsProfilePolicy and added source tests for approval, identity, level, relationship-history, pending-memory, owner-only metadata and photo-consent boundaries.
- PersonProfileStore now delegates active-session profile visibility to the tested policy.
- No tests were executed.


## AVA GLASS UI 2.0 design stage - 2026-11-17

- Created docs/AVA-GLASS-UI-2.0-DESIGN-SPEC.md as a design draft for owner review.
- The draft defines the visual system, screen layouts, navigation, component inventory, accessibility, privacy-aware states and implementation sequence.
- The numeric palette tokens are proposals; the overall dark-purple/lavender/rose-gold direction is already owner-approved.
- No UI source code was changed in this design stage. Coding is gated on design review/approval. Build, tests, CI, APK and device verification remain paused.


## AVA GLASS UI 2.0 owner approval — 2026-11-17

- Amir approved and froze the four primary destinations in order: خانه، گفتگو، حافظه، آوا.
- The fourth destination is the AVA hub, containing identity/personality, voice/language, AVA SECRET, daily assistant, project console, security/access and appearance/personalization.
- The canonical design specification records this approval and authorizes UI source implementation in a separate branch/PR.
- The implementation preserves the approved Sultan-lavender/pink, rose-gold and deep-purple palette, Persian RTL and native Android Views. Numeric token values remain subject to contrast review.
- This is design approval and source-work authorization only. Build, tests, CI, APK generation and physical-device verification remain prohibited; no such verification is claimed.


## AVA GLASS UI 2.0 source implementation — owner-approved baseline — 2026-11-17

- Source implementation is tracked on branch `implementation/glass-ui-2.0-approved`, based on the owner-approved design branch.
- Added centralized native-View theme tokens and reusable presentation components.
- Updated the app shell, four-tab navigation, current-page title, AVA hub, and in-app navigation history/back behavior.
- Added AVA hub extraction, accessible single-action tiles, 48 dp button minimums, primary/secondary/destructive button surfaces, page empty states, and task-delete confirmation.
- Home now points to the AVA hub rather than duplicating module shortcuts. Memory remains its own primary tab.
- Public-only Home/Conversation greetings are generic; idle voice state is not described as ready.
- Conversation composer supports multiline text up to four visible lines, bounded height, resize-aware keyboard behavior and draft retention across ordinary navigation; draft is cleared on privacy/session reset boundaries.
- UI implementation remains partial. Screen extraction, consistent state coverage, RTL/TalkBack/contrast review and complete control-to-service mapping remain open.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## GLASS UI 2.0 implementation review path — 2026-11-17

- Owner-approved design remains in Draft PR #4.
- Source implementation is in Draft PR #6 from `implementation/glass-ui-2.0-approved`, based on `design/glass-ui-2.0-spec`.
- Neither PR has been merged. Build, tests, CI, APK and physical-device verification remain paused.


## GLASS UI 2.0 explicit microphone control - 2026-11-17

- Added an accessible microphone button beside the chat composer, connected to the existing explicit speech-recognition flow.
- This does not enable ambient or always-on listening.
- No Build, tests, CI, APK or physical-device verification was performed.
