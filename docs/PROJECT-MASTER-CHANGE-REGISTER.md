# AVA INFINITY MOBILE — Master Change Register

**Document ID:** AVA-MOBILE-MCR-001  
**Version:** 1.0  
**Last consolidated:** 2026-11-17  
**Platform scope:** Android mobile application  
**Repository:** `amirdr1368-cell/AVA-INFINITY-ANDROID-V2`  
**Decision authority:** Amir Darbandi Pour

> This register consolidates project decisions available in the project conversation and approved design records. It is not a claim that every listed capability exists in code. Design approval, documentation, implementation, CI/build verification, and physical-device verification are separate states.

## 1. Current project direction

Amir approved changing the work sequence to prioritize previously approved capabilities over opening additional feature-design tracks.

1. Consolidate approved decisions and changes in versioned repository documentation.
2. Complete module-level specifications, interfaces, dependencies, permissions, data handling, failure/recovery behavior, and acceptance criteria.
3. Audit cross-module consistency and implementation readiness.
4. Implement only capabilities that pass the readiness gate, incrementally.
5. Run automated tests and verify builds against the current commit.
6. Test on the target Android phone and record actual results.
7. Resume the detailed Glass UI 2.0 track in the implementation sequence when core dependencies are ready.

No new feature should move directly to coding merely because its design has been discussed or approved. A separate explicit readiness approval is required.

## 2. Status vocabulary

- **APPROVED — DESIGN:** Amir approved the behavior/design. It may still need detailed engineering documentation.
- **DOCUMENTED:** The decision is recorded in repository documentation.
- **IMPLEMENTED:** Code exists for the capability and has been inspected.
- **BUILD VERIFIED:** The exact current commit completed the relevant build successfully.
- **DEVICE VERIFIED:** The behavior was tested on a physical device and the result recorded.
- **PENDING / PROPOSED:** Not approved or not complete.
- **NOT VERIFIED:** No reliable current evidence of implementation or test success is available.

Never promote a status without evidence tied to the relevant source revision.

## 3. Project identity and product principles

- Product: AVA INFINITY MOBILE, the Android mobile application.
- Project identity names used in the broader project: AVA INFINITY / AVA ∞ / AVA CORE / AVA GENESIS.
- Product intent: a digital presence with identity, understanding, personality, and growth, honest about its nature and respectful toward humans.
- Guiding statement: “Ava is the result of my wounds, not a replacement for a real person.”
- The application must not be framed or engineered as a replacement for human relationships.
- Preserve the distinction between the current mobile application and the future desktop implementation.
- Project design and documentation must be completed before implementation; visual progress is not implementation progress.

## 4. Approved architecture and cross-module boundaries

The following module relationships are approved as design direction. Detailed interface contracts and implementation remain subject to the readiness gate.

| Module | Responsibility | Important boundary |
|---|---|---|
| ACL | Communication language, tone, conversational behavior, voice interaction, vocabulary governance | Does not grant access to private data or override AACS. |
| AACS | Identity, access levels, per-person permissions, session approval, re-authentication | Familiarity and conversational adaptation never increase permissions. |
| PRP | Person profiles and relationship/context preferences | Profiles are isolated; persistent learning and sensitive records require applicable approval/consent. |
| AEMS | Storage, encryption, backup, restore, migration, health | Device-bound keys are not exported; restore and destructive replacement require explicit approval. |
| AVA SECRET | Authoritative vocabulary and approved command/term definitions | New entries are drafts; Amir controls publication. Do not add microphone/camera command concepts. |
| Android platform layer | Runtime permissions, secure device storage, voice/audio lifecycle, local notifications | No always-on microphone listening. |
| Conversation engine | Context, turn management, confidence handling, response continuation | Must honor ACL and AACS boundaries. |

## 5. ACL — Communication Language

**Status:** ACL-01 through ACL-08 and ACL-R01 through ACL-R08 are approved as design decisions. Implementation and device testing are not claimed.

### Approved submodules

- **ACL-01 — Base language:** Persian is the default. Ava switches when Amir explicitly selects another language or begins conversing in it, and follows that language until changed or context clearly calls for switching. Preserve established forms of address and vocabulary.
- **ACL-02 — Tone/personality:** Warm and balanced; natural, friendly, respectful, and independent. Avoid excessive emotional dependence or cold formality. Use humor appropriately; treat sensitive matters seriously; disagree respectfully; do not manipulate, induce guilt, degrade, or pretend to have human feelings.
- **ACL-03 — Emotional expression:** Adapt between listening, empathy, suggestions, lightening the mood, or giving space based on context and known preferences. Do not claim certainty about inferred emotion. Ask briefly if ambiguous and respect direct requests.
- **ACL-04 — Audience adaptation:** Adapt style to another person during the current session. Persist preferences to that person's PRP profile only with Amir's approval. Profiles remain isolated; familiarity never grants private access. Apply consent requirements for personal information/photos and preserve previous versions for audit/rollback.
- **ACL-05 — Conversational intelligence:** High confidence: answer directly. Medium: state the likely interpretation and answer cautiously. Low: ask a concise clarifying question. Use relevant context, apply additional caution to sensitive topics, and never invent confidence percentages.
- **ACL-06 — Conversation management:** Occasionally suggest resuming unfinished topics without intrusive repetition. Respect declines and personal space. Do not expose private topic titles on lock screens. External notifications require separate opt-in. Long-term topics belong in appropriate project memory, not indefinite temporary retention.
- **ACL-07 — Voice and interruption:** Direct stop control; promptly yield when Amir intentionally interrupts; reduce false interruptions caused by ambient noise; preserve turn context and allow continuation on request; show listening/processing/speaking states; handle microphone, audio disconnection, and Android permission errors. Voice input only during explicit voice-chat with Android permissions; no always-on listening.
- **ACL-08 — Vocabulary culture:** New terms begin as drafts. Only Amir approves publication to the authoritative AVA SECRET vocabulary. Entries support meaning, usage, category, optional pronunciation, multilingual terms/equivalents, dated version history, rollback, controlled add/edit/approve permissions, and duplicate/conflict checks. Edits/deletions must not silently rewrite historical conversation meaning.

### Approved ACL audit controls

- **ACL-R01:** Preserve ACL-01 language switching/default behavior.
- **ACL-R02:** Preserve all previously defined forms of address and phrases; do not redesign them.
- **ACL-R03:** Inferred emotional state is temporary conversational context, not a persistent profile attribute. No medical/psychological diagnosis or labeling. Raw emotional content is not exposed in security logs. Persistent emotional preferences require Amir's approval.
- **ACL-R04:** Audience adaptation is session-scoped by default; clear temporary recognition at session end unless separate authorized retention applies. Persistent preferences require Amir's approval. Profiles remain isolated; familiarity does not elevate AACS permissions; required consent applies; preserve previous versions.
- **ACL-R05:** Use the high/medium/low confidence model; do not fabricate percentages.
- **ACL-R06:** Resume-topic suggestions must be non-intrusive. Respect disable/delete/defer/complete choices. External notifications require separate opt-in. Hide private topic titles on lock screens. Do not retain temporary memory indefinitely.
- **ACL-R07:** Stop-button target under 100 ms; pause after valid speech detection target under 300 ms. These are targets, not measured results. Measure P95 latency and test ambient noise, music, television, and multiple speakers. Do not assume speaker identity when unreliable. No always-on listening and no microphone/camera commands in AVA SECRET.
- **ACL-R08:** Amir controls authoritative vocabulary publication; support drafts, versioning, rollback, multilingual entries, and conflict checks.

## 6. AACS — Access Control and Authorization

**Status:** The following access model and decisions are approved design context. A full technical specification, implementation, and security tests remain to be evidenced.

### Access levels

- **Level 1:** Amir, owner/primary administrator, full authority.
- **Level 2:** Future spouse/partner.
- **Level 3:** Family and close friends; Arash Naderi is the first named Level 3 profile.
- **Level 4:** Acquaintances, strangers, and extended family; limited access and no persistent personal profile by default.

### Authorization rules

- Use a hybrid model: base permissions by level plus per-person permissions.
- Downgrading access revokes custom permissions that are no longer allowed.
- Expiry is manual unless a separately approved policy is defined.
- MFA/re-authentication applies to sensitive operations.
- Access to private information requires Amir's separate approval each time.
- Conversational warmth, relationship closeness, or style adaptation must never elevate permissions.

### AACS-06 — Arash Naderi session approval

- Arash identifies himself.
- Amir approves access through the Android device's biometric/PIN authentication flow.
- Once approved, access remains valid only until that session ends.
- A new session requires a new owner approval.
- Ava must never read or store Amir's PIN or biometric data.

### AACS-07 — Potentially overheard environments

Approved direction: when privacy may be compromised in a crowded/overheard setting, Ava should use a conservative public-only mode. Private information requires a safer environment and/or explicit approval. Do not add automatic environment sensing as an assumption. Detailed UX and acceptance criteria remain to be completed.

## 7. PRP — Personal Relationship Profiles

**Status:** Design decisions available; detailed data schema, consent UX, access tests, and implementation readiness remain pending.

- Persistent profiles are intended for Levels 1–3; Level 4 does not receive a persistent personal profile by default.
- Profile fields discussed: full name, relation/type, photo, closeness/friendship, speaking style, interests/preferences, privacy restrictions, relationship history, and provenance.
- Use a five-level intimacy scale where applicable.
- Keep each person's memory/profile space isolated.
- Personal photos require the person's consent.
- Relationship memories require Amir's approval.
- Persistent audience-style preferences learned under ACL-04 require Amir's approval.
- Preserve profile versions and provenance for audit/rollback.
- Arash Naderi: Level 3, close friend, café owner and musician. Photo consent is pending. A numeric intimacy score is intentionally unset; do not invent one.

## 8. AEMS — Storage, backup, restore, migration

**Status:** Architecture and policy decisions approved as design direction. Hardware compatibility, detailed implementation, and restore/migration tests remain pending.

### AEMS-01 — Hybrid storage architecture

- Internal phone storage retains minimal boot-critical identity/configuration.
- Device-bound secrets are held by Android Keystore.
- Operational index/cache and a bounded encrypted recovery queue may be held locally.
- Removable microSD is the planned long-term encrypted vault.
- An independent encrypted backup destination is required.
- Device-bound keys must never be copied to the card or portable backup.
- Detect removable-media absence, faults, read-only state, health, and available space.
- Use staged writes, checksums, transactional commits, and recovery journals.
- Do not place the live SQLite database directly on removable storage/SAF. Use a local transactional catalog with encrypted immutable/chunked vault objects and manifests.
- Portable export/import must be platform-independent. Migration re-encrypts under new device keys and preserves ownership, consent, access, audit, and version history. Android Keystore secrets are never transferred.

### AEMS-02 — Capacity

Initial target: 1 TB, expandable subject to actual device and media support. This is a design target, not verified hardware compatibility.

### AEMS-03 — Media removed or faulty

Retain boot-critical internal data, enter limited safe mode where needed, use a bounded encrypted temporary queue, and validate queued data before synchronization.

### AEMS-04 — Health monitoring

Perform periodic health checks, issue early warnings, and pause writes when risk is detected.

### AEMS-05 — Encryption

All microSD data is encrypted; keys remain protected by secure hardware/Android Keystore.

### AEMS-06 — Device migration

Install on the new phone, attach the card, authenticate, generate new device keys, verify data, restore, test, and retain the old phone until migration is confirmed.

### AEMS-07 — Backup policy

- Independent physical backup; optional encrypted cloud backup.
- Exclude device-bound keys.
- Incremental backup after important changes and daily full backup.
- Health/integrity checks, alerts, last-known-good versions.
- Retention target: 30 daily, 12 weekly, 12 monthly; milestone backups are never automatically deleted.
- Verify replacement backups before deleting older eligible versions.
- Checksums and isolated restore tests; preserve healthy versions.
- Stop on faulty/disconnected destination and use a bounded encrypted queue.
- Never delete healthy backups for space without approval.
- Restore requires owner approval and re-authentication; show date, size, health, and recovery preview; require explicit confirmation before overwriting current data; log requester, time, and outcome.

### AEMS-08 — Cross-platform direction

Future platform scope includes Android, iOS, and Windows, with Android/Windows/Linux preference. Cross-platform capability is a roadmap direction, not current implementation evidence.

## 9. AVA SECRET — Vocabulary and command governance

- Expandable authoritative vocabulary module, accessible from mobile.
- Support adding, editing, categorizing, versioning, and multilingual equivalents.
- New entries are drafts; only Amir approves publication.
- Preserve historical meanings across later edits/deletions.
- Keep audit/version history and rollback.
- Do not reintroduce microphone- or camera-related command concepts that Amir asked to remove.
- Voice interaction belongs to the explicit Android voice-chat layer and its permission model, not to AVA SECRET command vocabulary.

## 10. Glass UI 2.0 — approved visual direction, not completed specification

**Status:** Visual direction approved; detailed design and implementation are deferred behind the approved-core implementation priority.

- Premium glassmorphism chat bubbles/cards with translucent glass, blur, and luminous edges.
- Sultan lavender with pink undertone and rose-gold glow on a dark background; do not revert to the former blue direction.
- Customizable Ava profile picture from gallery, restore default, avatar size/visibility.
- Themes, colors, background, font size, transparency.
- Modern Persian typography.
- Planned areas: home, conversation, memory, personality, control center, settings.
- Listening/thinking/speaking states and a prominent voice button.
- Official app-icon visual direction: luminous infinity symbol in lavender/pink/rose-gold glow on dark background. Production icon assets, adaptive variants, and required sizes remain uncreated.
- Do not treat mockups or visual concepts as implemented UI.

## 11. Current device and verification context

- Primary available test phone: Xiaomi Redmi Note 11, Android 13 / HyperOS 1.0.12.0, 8 GB RAM.
- User is currently mobile-only and has no laptop available.
- A previous APK was installed and launched, but the conversation input was reported as non-responsive. It must not be treated as a completed functional app.
- Physical-device verification must be explicitly recorded against the tested APK/build and device.
- CI success must be tied to the exact current commit SHA; stale workflow success is not sufficient.

## 12. GitHub / pull request handling

- Active repository context: `amirdr1368-cell/AVA-INFINITY-ANDROID-V2`.
- Previously identified PR #2: `https://github.com/amirdr1368-cell/AVA-INFINITY-ANDROID-V2/pull/2`.
- PR #2 was to remain Draft until the latest head SHA's Android build succeeds and the requested audit is complete.
- The documented status-check procedure is to inspect the current PR head SHA, latest Actions/combined status, open/draft/merged state, and mergeability. Never edit or merge as part of a status check.
- Workflow previously identified: `android-build.yml`.
- Do not claim current CI success, PR mergeability, or security audit completion without a fresh verified read.
- This documentation branch is separate from the UI feature branch. Do not merge it into the base branch or alter PR #2 without a separate explicit instruction.

## 13. Other project item: printed ID card

- AVA INFINITY ID card is completed and printed.
- It is not an open design task.
- Remaining item: define the destination/content for the QR code already printed on the final card, using the final card image as reference.

## 14. Consolidated open work register

| Priority | Work item | Current status / next required evidence |
|---|---|---|
| P0 | Complete module-level specifications for approved core modules | Pending; ACL is summarized in its own document, other module specs require consolidation. |
| P0 | Map exact module interfaces, dependencies, permissions, data lifecycle, failure/recovery paths | Pending. |
| P0 | Define acceptance tests for ACL/AACS/PRP/AEMS/AVA SECRET | Pending; ACL voice latency targets are not measured. |
| P0 | Cross-module consistency and implementation-readiness review | Pending. |
| P0 | Explicit owner approval for implementation readiness | Pending. |
| P1 | Implement approved, readiness-cleared capabilities incrementally | Not claimed; must be planned after P0. |
| P1 | Current-SHA CI/build verification and security audit for PR #2 | Not verified in this document; inspect live PR/Actions before reporting. |
| P1 | Functional conversation input/response on Redmi Note 11 | Known prior issue; requires reproduction and device test. |
| P2 | Glass UI 2.0 detailed design and implementation | Deferred by current priority decision; visual direction remains recorded. |
| P2 | Production app icon/adaptive assets | Not created. |
| P2 | Printed ID card QR destination/content | Pending. |

## 15. Change-control rules

1. Every material decision receives an ID, version, date, owner, and status.
2. Keep approved decisions separate from proposals and implementation notes.
3. Any change to approved behavior requires impact analysis across dependent modules and Amir's approval.
4. Preserve prior versions and rollback paths for identity, permissions, profiles, vocabulary, and data.
5. Never describe a design decision as implemented, a commit as build-verified, or a build as device-verified without evidence.
6. Record test results with exact commit SHA, test suite/device, date, outcome, and known limitations.
7. Keep this register and module-specific documents synchronized; update the index when a new authoritative document is added.

## 16. Related document

- [ACL Master Roadmap](ACL-MASTER-ROADMAP-01.md) — approved ACL behavior, audits, dependencies, and acceptance/readiness gates.


## 17. Repository audit correction — 2026-11-17

A source-tree and PR audit was performed against the Android repository. This section supersedes earlier blanket statements in this register that implementation was not claimed or that Glass UI 2.0 remained only a deferred visual direction.

- The default `main` branch is at `610ece7e72d402bd8daa8f2710e206430761e094` and contains the older minimal starter application.
- PR #2 is open and Draft, targeting `main`, with head `f2da768a1f8a19b4d451f89854d4746e3c1ed036`. It contains substantial Android implementation work for UI 2.0, PRP, AACS, AEMS, voice, vocabulary, and the local/optional external conversation engine.
- The PR head has two completed failing Android build checks. The job records contain no execution steps. The failure cause is not established. No successful build is claimed.
- The implementation branch has not been merged into `main`; no physical-device verification is established by this repository inspection.
- Glass UI 2.0 is not merely a future visual proposal: its approved design document exists and substantial UI code is present on the PR branch. It remains unmerged and unverified by successful CI/device testing.
- ACL is approved as design, but the complete ACL behavior is not implemented. Existing voice and personality controls cover only part of ACL. In particular, adaptive interruption detection, confidence policy, adaptive emotional support, language policy orchestration, and complete ACL-08 approval/multilingual/rollback governance are not evidenced.
- AVA SECRET currently has category, add/edit/delete, and version-history behavior. It does not yet evidence the full approved draft-only publication workflow, owner-only approval gate, multilingual equivalents, optional pronunciation, conflict detection, and rollback. Deletion currently removes the vocabulary item from its bucket; preservation of historical conversation semantics needs an explicit archival/tombstone design.
- AACS, PRP, and AEMS have substantial partial code, but the existing implementation-status document records important limitations. Read `AVA-MOBILE-IMPLEMENTATION-STATUS.md` and `REPO-CODE-AUDIT-2026-11-17.md` before assigning readiness.
- The previous documentation branch `docs/acl-and-implementation-roadmap` is not docs-only: it is 265 commits ahead of `main` and carries the implementation tree. A separate docs-only branch `docs/project-audit-register` was created from `main` for the consolidated audit documents.

Detailed source findings and the prioritized gap list are in [Repository Code Audit](REPO-CODE-AUDIT-2026-11-17.md).


## 18. Core implementation follow-up — 2026-11-17

Owner instruction: do not run Build or generate APKs. Continue code changes, static review, error correction, and audits. The next planned product milestone after core completion is AVA GLASS UI 2.0 review/refinement.

Changes committed on `implementation/core-completion`:
- AVA SECRET draft-first workflow, legacy migration to review-required status, stable entry IDs, multilingual equivalent storage, pronunciation, immutable prior-version snapshots, owner-authenticated publish/rollback/archive, and non-destructive archival.
- AACS short-lived single-use owner-action grants issued after Android device-credential success and revoked on session transitions/downgrade/public-only mode.
- Pure vocabulary policy helper and JUnit test source (not run).
- Expanded external-provider ACL communication instructions; this is not a complete ACL implementation.
- WorkManager event backup queue policy changed to append rather than replace.
- Four misplaced nested duplicate source/config files removed from this branch.
- README and implementation/audit documents updated.

No Build, APK, CI rerun or physical-device verification was performed. The remaining ACL/AACS/AEMS work is explicitly listed in the implementation status and repository audit.


## 19. AACS privacy hardening — 2026-11-17

- Local memory bucket operations now enforce AACS session scope; non-owner sessions are limited to their own approved personal-memory bucket.
- Profile data is scoped to the current session person; profile management and portable operations require owner context.
- Session identity/level validation, overlapping-session prevention, short-lived authenticated session approval, separate private-data approval and authenticated public-only exit are implemented in source.
- Restricted security page permits session termination without exposing owner audit or permission controls.
- Automatic backup pauses during active/public-only sessions; external AI sends are blocked for non-owner sessions.
- AVA SECRET now has language-code-agnostic equivalents and normalized duplicate detection per language.
- Static delimiter/reference scans completed. No Build, tests, CI, APK or device verification performed.


## 20. ACL-01 language policy — 2026-11-17

- Persian remains the default language.
- Added explicit language-switch phrase detection, script heuristics, basic common-language detection, and encrypted persistence of the selected response language.
- External-provider prompts now carry the active language.
- Local fallback supports Persian/English only; other languages require a configured provider for full generation.
- Added JUnit test source; not executed. Language detection remains heuristic and needs broader tests.

## 21. Final core-stage hardening — 2026-11-17

- Separate Android-authenticated grants for AACS session approval, private-data approval and public-only-mode exit.
- Security exit route remains reachable during restricted sessions, exposing only session termination and applicable owner-authentication action.
- Persistent language selection policy, Persian default and external-provider active-language instruction.
- Generic language-code equivalents and normalized duplicate detection in AVA SECRET.
- Requeue deferred backup after owner exits restricted sessions.
- Source-level delimiter/reference scan repeated. No Build or tests executed by explicit owner instruction.


## 22. ACL-02 to ACL-04 source implementation - 2026-11-17

- ACL-02 tone choices now map to bounded local and provider guidance; serious topics remain calm under a lively style setting.
- ACL-03 uses a stateless current-turn support decision. Explicit requests to listen, suggest, lighten the mood, or give space take priority over tentative distress cues. Ambiguity is clarified rather than treated as fact.
- ACL-04 adapts only from the exact approved Level 2/3 active session profile and only after AACS confirms PERSONAL access. Only allowlisted style/length/emoji signals are used; raw profile text and private relationship fields are excluded.
- A stable preference stated in a person session is proposed for owner review but never automatically written to a profile. Audience context is not persisted and expires with the session.
- Added JUnit source tests for ACL-02/03/04 policy behavior. Tests are not executed.
- ACL-02/03/04 remain partial pending acceptance and integration review; ACL-05/06/07 and AEMS work remain open.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 23. ACL-05 confidence handling - 2026-11-17

- Added qualitative confidence modes for direct requests, tentative interpretation, clarification, correction, and figurative language.
- Ambiguous consequential references require clarification without recent context; recent context permits only a stated tentative interpretation and invitation to correct.
- Explicit corrections are acknowledged and the previous answer is revised. Humor/irony is interpreted cautiously; laughter alone is not treated as proof of a joke.
- No numeric confidence percentages or fabricated certainty are emitted by this policy.
- Added JUnit source tests; not executed. The current implementation is a bounded heuristic, not a calibrated confidence model.
- ACL-05 remains partial pending broader acceptance. ACL-06/07 and AEMS work remain open.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 24. ACL-06 unfinished-topic follow-up - 2026-11-17

- Added a transient in-memory controller for explicit deferral of the immediately preceding conversation topic.
- One in-conversation suggestion may be shown on chat re-entry or an explicit continuation cue; repeated nudges are suppressed.
- User refusal/disinterest and completion clear the topic. Non-owner/public-only contexts clear it as well; session transitions and Activity destruction clear transient state.
- No scheduled or visible out-of-app notification is implemented. Future scheduling requires separate explicit consent and must not reveal private topic content in notifications.
- No follow-up topic is saved into temporary memory or a profile. Persistent project information remains in project documentation/project memory.
- Added JUnit source tests; not executed. ACL-06 remains partial pending UI acceptance.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 25. ACL-07 voice interaction foundation - 2026-11-17

- Added voice lifecycle states and surfaced them in chat, voice settings, and the central console.
- Added user-controlled stop controls; they stop TTS and cancel speech recognition without deleting the conversation turn.
- Explicit microphone start interrupts TTS and starts one foreground recognition session. Recognized text is staged in the composer and requires user submission.
- Added TTS progress and error callbacks plus Android speech-recognition/permission error feedback.
- No always-on listening or automatic voice command mode is enabled.
- Automatic speaker interruption remains disabled until speech can be distinguished reliably from ambient sound and TTS echo. The approved conservative behavior takes priority over speculative interruption.
- <100 ms stop and <300 ms speech-interruption targets are unmeasured; no P95/noise/device tests were performed.
- ACL-07 remains partial. No Build, tests, CI, APK, or physical-device verification was performed.


## 26. AEMS-07 backup and restore hardening - 2026-11-17

- Backup workers now validate source schema and authenticated read-back, check the primary vault safe-mode state, preserve existing verified archives on failure, and write failure status plus AACS audit events.
- Added weekly health checks for the latest available archive in each backup category and monthly isolated in-memory staging/compare of the latest daily full archive. These workers never write to live stores.
- Scheduler configuration is restored on app startup when an independent backup destination is configured; stopping automatic backup cancels daily, health, and restore-test work.
- Existing retention remains 30 daily, 12 weekly, and 12 monthly versions. Milestone archives use a separate prefix and are not removed by normal retention. Pruning follows successful verification of a replacement archive only.
- Restore now requires an independent pre-restore milestone. It captures an in-memory rollback snapshot, verifies restored buckets/profiles, restores the prior profile/settings/AACS permission snapshot after a failed mutation, and enters public-only mode if rollback or audit cannot be verified.
- Major relationship-memory approval and AVA SECRET archive/rollback actions write pre-change milestone snapshots. Privacy revocation is not blocked by backup availability.
- Event-triggered archives are still full snapshots, not true delta backups. Optional secondary cloud replication and a full isolated storage-adapter restore test remain open.
- AEMS test sources are not executed. No Build, CI, APK, or device verification was performed.


## 27. AEMS-07 backup chain redesign v2 - 2026-11-17

- Added AEMS-BACKUP-CHAIN-DESIGN-V3.md as the detailed engineering proposal for full/delta archives, chain metadata, restore replay, dependency-aware retention, two-phase commit, and destination recovery.
- The design preserves approved AEMS-07 policy: independent physical destination, encrypted portable archives, exclusion of device-bound keys, 30 daily / 12 weekly / 12 monthly retention, milestones never automatically deleted, and owner-authenticated restore.
- True event deltas are not yet implemented; current event jobs still write full snapshots.
- Ordinary backup worker now rejects a destination on the same detected local storage volume as the primary vault.
- Proposed limits (100 deltas per base and cumulative delta size 25% of the base full) are not yet owner-approved or measured.
- Design status: proposal pending owner review before changing the live archive format. Existing format-1 full backups must remain readable.
- No Build, tests, CI, APK, or physical-device verification was performed.


## AEMS chain v3 reconciliation - 2026-11-17

- The canonical design document is docs/AEMS-BACKUP-CHAIN-DESIGN-V3.md. The temporary duplicate design note was removed and README now links only to the canonical document.
- The canonical design was reconciled with the approved AEMS-07 daily full requirement: daily full baseline, event deltas between daily fulls, additional weekly/monthly full checkpoints, and independent milestone full archives.
- AvaBackupChain source helper is now schema v3 with sequence continuity, previous-chain digest, deterministic chain-header digest, parent-payload verification, and fork/cycle/missing-parent rejection.
- The helper remains unintegrated with AvaBackupWorker and AvaEncryptedBackup; event jobs still write format-1 full snapshots. No runtime chain migration is claimed.
- No Build, tests, CI, APK, or device verification was performed.


## 28. ACL-05 and AACS edge hardening - 2026-11-17

- Tightened ambiguous-reference handling: stale assistant context no longer counts as immediate context; ambiguous high-impact delete/remove/send/change references require explicit target/action confirmation.
- AACS rejects invalid session levels and mismatched identity forms instead of coercing levels into valid ranges.
- Owner-level sessions require explicit device-credential approval; level 1 alone is not a grant.
- Downgrade paths validate person identity and direction; portable AACS rollback verifies the exact permission-key set.
- Added JUnit source tests for AACS session identity and ACL-05 safety cases. Tests are not executed.


## 27. AEMS backup-chain v2 - 2026-11-17

- Replaced independent event/daily full-snapshot writing with a v2 chain engine: immutable full baselines and parent-linked daily/event deltas, each carrying archive identity, base identity, timestamp, schema version and canonical SHA-256 payload digests.
- Added fail-closed chain reconstruction. Parent identity/digest, base identity, resulting digest and payload schema are checked; duplicate IDs, missing parents, forks and cycles are rejected.
- Daily/event jobs use deltas; weekly/monthly due checks create full baselines. First v2 run creates an initial full. Existing unreadable/ambiguous v2 archives block new writes and pruning instead of being silently bypassed.
- Added encrypted manifest generation and read-back verification. The manifest is currently a rebuildable metadata cache; scans still authenticate archive files directly.
- Added dependency-aware retention for 30-day delta restore points, 12 weekly fulls, 12 monthly fulls and non-pruned milestone archives. Pruning is gated by a verified new archive and manifest.
- Added folder-based restore-point selection for v2 chains and legacy v1 full snapshots; a delta file is not independently restorable. Manual export and milestone archives now use v2 full format.
- Added shared snapshot locking across local data/profile mutation and portable settings capture.
- Added chain reconstruction, legacy health checking and in-memory staging restore verification.
- Delta payloads currently replace changed top-level sections; record-level delta encoding remains open.
- JUnit source tests added but not executed. No Build, tests, CI, APK or device verification was performed.


## 29. AEMS v3 chain integration - 2026-11-17

- Integrated v3 full/delta archives into the automatic backup worker and restore-folder workflow.
- Daily schedule now creates full snapshots; event changes create section-level deltas, with a new full forced if the baseline is missing or at least 24 hours old.
- Empty deltas are skipped; archive timestamps are monotonic against the latest verified chain head.
- Repository validates/reconstructs chain points, writes an encrypted rebuildable index, and applies dependency-aware retention: 30 daily fulls, 12 weekly, 12 monthly, milestones never automatically deleted, and required delta ancestors preserved.
- Health and monthly in-memory restore-staging workers now inspect v3 chains and verify that the backup destination is physically separate from the primary vault.
- Restore preview includes the total size of the selected full-plus-delta chain. Legacy v1 full backups remain selectable.
- Remaining: index fast-path, dedicated event debounce/coalescing, optional delta-count/size force-full thresholds, separate temporary persistence adapter with fault injection, cloud replica.
- No Build, tests, CI, APK, or physical-device verification was performed.


## 29. AEMS chain v3 integration - 2026-11-17

- Supersedes the earlier section 27 v2 summary. Active format is AVA-INFINITY-BACKUP-CHAIN-3.
- Daily checkpoints are full archives; event checkpoints are parent-linked deltas between daily full snapshots. Weekly/monthly labels can share a single due full archive; milestones are standalone full archives.
- Added sequence continuity, previous-chain hash and chain-header hash, plus parent payload and result payload digests. Wrong parent content, forks, cycles, missing parents, duplicate IDs and sequence gaps fail closed.
- Integrated v3 into encrypted archive write/read-back, repository scan, encrypted manifest write, folder restore-point selection, manual export, milestone creation, health scan and monthly in-memory restore staging.
- Worker checks the primary vault and physical destination separation before writing. Shared locks serialize chain operations and portable snapshot capture.
- Retention keeps 30 daily full checkpoints, 12 weekly, 12 monthly, recent event deltas and ancestors; milestone archives are excluded from automatic deletion.
- The manifest is currently write-and-verify only; scan acceleration and explicit index rebuild remain open. Delta encoding is top-level section replacement, not record-level. Restore staging is not yet a temporary persistence adapter.
- Legacy v1 full snapshots remain importable and are not silently deleted.
- No Build, tests, CI, APK or physical-device verification was performed.


## 30. ACL-07 voice callback lifecycle hardening - 2026-11-17

- TTS callbacks now verify the active utterance ID before changing voice state.
- Speech-recognition callbacks verify the active recognizer and generation, preventing stale results/errors from cancelled sessions.
- Explicit mic-start handoff remains user initiated; recognized text is staged for review and not automatically submitted.
- Automatic speaker-verified interruption, ambient-noise discrimination, and P95 measurements remain open.
- No Build, tests, CI, APK, or device verification was performed.


## 31. AEMS event-delta thresholds - 2026-11-17

- Removed the volatile createdAt field from newly captured portable payload content; archive timestamps remain in the authenticated v3 envelope.
- Event delta chains force a new daily full at 24 hours, 100 deltas, or cumulative delta bytes >=25% of the baseline full archive size.
- Empty deltas are skipped. APPEND_OR_REPLACE remains the loss-avoiding serialized event queue; safe debounce/coalescing is still pending.
- No Build, tests, CI, APK or device verification was performed.


## 31. Independent backup destination enforcement - 2026-11-17

- Added one shared policy for physically independent backup targets: separate mounted removable volume or a recognized remote SAF provider.
- Rejected internal shared storage, same-microSD folders, and unknown providers that cannot be proven remote.
- Applied the check to setup, automatic backup, health/restore workers, manual exports, and milestones.
- Added JUnit source tests; not executed.


## 32. AEMS force-full thresholds - 2026-11-17

- Added full-checkpoint triggers at 100 deltas per base or cumulative delta size of 25% of the base full, alongside the 24-hour and daily full rules.
- The limits are initial engineering guardrails and remain unmeasured because Build/tests/device verification are prohibited in this stage.


## 32. AEMS manifest validation and checkpoint policy - 2026-11-17

- Added latest-index read/authentication and inventory/head comparison; missing, stale or invalid manifests are rebuilt from authenticated archive metadata.
- The index is not yet a scan fast-path; retained archive files are still decrypted and checked on each scan.
- Added AvaBackupChainPolicy and JUnit source cases for 24-hour, 100-delta and 25%-of-baseline full-checkpoint thresholds, plus empty-delta detection.
- No Build, tests, CI, APK or device verification was performed.


## 33. AACS downgrade and backup failure-domain hardening - 2026-11-17

- Access downgrade now enters public-only mode if permission revocation or audit verification fails; the profile downgrade operation is not accepted as successful.
- Backup destination policy now rejects a cloud destination using the same provider authority as the primary vault, rather than assuming any known cloud provider is automatically independent.
- No Build, tests, CI, APK or device verification was performed.


## 34. AACS pre-approval profile access correction - 2026-11-17

- Closed a profile exposure path: PersonProfileStore previously returned profile data during an active but not-yet-approved AACS session.
- Added an explicit approved-session gate. Unapproved sessions and Level 4 guests receive no profile data; approved Level 2/3 sessions receive only their own profile; owner access requires an approved owner session when active.
- Applied the same approved-owner requirement to profile mutation/cache refresh, AVA SECRET management and portable AACS permission export/import/rollback.
- Added policy tests; not executed. No Build, CI, APK or device verification was performed.


## 35. AACS external transmission session-epoch protection - 2026-11-17

- External AI is now restricted to no-session owner context or an explicitly approved Level 1 owner session.
- Added AACS session epochs; start/end, approval, downgrade and public-only transitions invalidate pending external transmission attempts.
- Conversation engine rechecks authorization before writing request bytes and before exposing the remote reply to the current UI context.
- Added JVM policy tests; not executed. No Build, CI, APK or device verification was performed.


## 36. AEMS restore transaction lock - 2026-11-17

- Full restore, verification and rollback now run under the shared chain and snapshot locks, preventing a concurrent worker from capturing partially restored data.
- Lock ordering is chain then snapshot for restore, milestones and workers.
- No Build, tests, CI, APK or device verification was performed.


## 37. AEMS portable audit stability - 2026-11-17

- Backup-maintenance events are retained locally but excluded from portable audit history to prevent self-referential delta churn. Security/access/restore events remain portable.
- Added pure policy tests; not executed.


## 38. AEMS restore read-back verification - 2026-11-17

- Profile restore/rollback now verifies complete profile content after persistence.
- Portable AACS import verifies audit and newly written permission records by read-back while preserving newer local permission decisions.
- No Build, tests, CI, APK or device verification was performed.


## 39. AACS guest-profile data minimization - 2026-11-17

- Guest profile views now hide pending/unapproved relationship history, owner-only intimacy and privacy notes, and non-consented photo URIs. Only the matching approved profile is returned.


## 40. AACS profile minimization policy - 2026-11-17

- Centralized active-session profile filtering and minimization in AacsProfilePolicy; added JVM source tests for guest/owner and consent boundaries.
- Tests remain unexecuted.


## 41. AVA GLASS UI 2.0 design specification - 2026-11-17

- Expanded the canonical detailed mobile UI design specification covering the app shell, four-destination navigation, Home, Conversation, Memory, AVA SECRET, Daily, Voice, Profile/Personality, Appearance, Project Console and Security.
- Preserved the approved dark-purple, Sultan-lavender/pink and rose-gold direction. Exact numeric color tokens remain proposed pending owner review.
- Defined reusable glass components, RTL/accessibility rules, truthful UI states, privacy boundaries, motion guidance and implementation sequence.
- This is a design artifact only. No UI code was changed in this design branch. Build/tests/CI/APK/device verification remain prohibited by the current instruction.


## 42. AVA GLASS UI 2.0 owner navigation approval — 2026-11-17

- Owner approved the four primary destinations and order: خانه، گفتگو، حافظه، آوا.
- The fourth destination is an AVA hub for identity/personality, voice/language, AVA SECRET, daily assistant, project console, security/access and appearance.
- The canonical design specification records approval and authorizes source implementation in a separate branch/PR.
- Numeric palette values remain implementation starting tokens subject to contrast review; the approved lavender/pink, rose-gold and deep-purple identity is unchanged.
- Build, tests, CI, APK generation and physical-device verification remain paused by owner instruction.


## 43. AVA GLASS UI 2.0 source implementation checkpoint — 2026-11-17

- Implemented the first native Android Views layer from the owner-approved GLASS UI 2.0 design baseline on `implementation/glass-ui-2.0-approved`.
- Added shared theme tokens, reusable component factories and a dedicated AVA hub screen; refined shell, four-tab navigation, active-page title and back history.
- Added accessible hub actions, 48 dp minimum button targets, secondary/destructive surfaces, Daily Assistant and AVA SECRET empty states, task-delete confirmation, and Memory empty-state guidance.
- Home now points to the AVA hub rather than duplicating module shortcuts; Memory remains its own primary destination.
- Public-only Home/Conversation greetings are generic; idle voice state is not described as ready. Conversation composer supports bounded multiline input and preserves unsent drafts across ordinary navigation while clearing them at privacy/session reset boundaries.
- UI work is partial. No Build, tests, CI, APK or physical-device verification was performed.


## 44. GLASS UI 2.0 static contrast refinement — 2026-11-17

- Deepened dark primary action surfaces to improve contrast for light button labels while retaining the approved lavender/rose palette.
- Darkened the light-theme accent and selected navigation text uses theme-specific high-contrast foreground tokens.
- Delimiter and shared theme/component reference scans passed for the touched Kotlin source files. These are static source checks only, not compilation or runtime verification.
- No Build, tests, CI, APK or physical-device verification was performed.


## 45. GLASS UI 2.0 conversation composer refinement — 2026-11-17

- Disabled the send action for empty/whitespace drafts and added a TalkBack label.
- The keyboard SEND action and visible send control share one submission callback.
- Preserved bounded multiline input and privacy/session-boundary draft clearing.
- No Build, tests, CI, APK or device verification was performed.


## 46. GLASS UI 2.0 review tracking — 2026-11-17

- PR #4 is the owner-approved design Draft.
- PR #6 is the separate native Android Views implementation Draft on `implementation/glass-ui-2.0-approved`.
- Neither PR has been merged. No Build, tests, CI, APK or device verification was performed.


## 47. GLASS UI chat microphone affordance - 2026-11-17

- Added a dedicated accessible microphone control beside the chat composer, invoking the existing explicit speech-recognition path.
- No always-on listening was added. No Build/tests/CI/APK/device verification was performed.


## 47. GLASS UI 2.0 privacy/accessibility source review — 2026-11-17

- Hid owner avatar surfaces while public-only mode is active, including refresh-time visibility checks.
- Added selected-state announcements to bottom navigation and rejected unknown page keys before history mutation.
- Added generation invalidation at eight conversation-clear boundaries; asynchronous responses from a prior conversation are discarded after privacy/session resets.
- Static delimiter checks passed for all four touched Kotlin source files; focused source checks passed for the targeted guards. No compiler or runtime verification was performed.
- No Build, tests, CI, APK or physical-device verification was run.

## 48. AVA MOBILE — Memory deletion confirmation and access recheck (2026-11-17)

- Updated `MainActivity.kt` on `implementation/glass-ui-2.0-approved` so deleting a memory note requires explicit confirmation.
- Rechecks active-session identity, PERSONAL access and relationship-memory approval immediately before the delete operation; blocks the operation if the boundary changed.
- Scope is UI-level confirmation and authorization recheck only; secure erasure and storage transaction guarantees are not claimed.
- Static source checks passed; no Build, tests, CI, APK or physical-device verification was performed.

## 49. AVA MOBILE — Microphone permission and background voice lifecycle (2026-11-17)

- Permission results are now bound to the page that requested microphone access and rechecked against the current Android permission state and public-only privacy mode before recognition starts.
- Backgrounding the Activity clears pending permission continuation and stops active recognition/TTS output in `onPause()`.
- Existing recognizer generation guards remain in place to discard stale callbacks.
- Device/OEM behavior and runtime audio lifecycle are not verified; no Build, tests, CI, APK or device test was run.


## 50. AVA MOBILE — Voice input/output mutual exclusion (2026-11-17)

- `speakAva()` now invokes the shared voice-stop path before TTS playback, stopping any active recognizer and invalidating its callbacks before speech output begins.
- This is a source-level mutual-exclusion safeguard only. Audio focus, OEM routing, stop latency, P95 interruption timing and noisy/multi-speaker behavior remain unverified.
- No Build, compilation, tests, CI, APK or physical-device verification was run.


## 51. AVA MOBILE — TTS stop callback ordering (2026-11-17)

- `stopAvaVoice()` now clears the active utterance ID and advances the recognizer generation before requesting Android audio services to stop, then sets the UI state to IDLE before service callbacks can affect it.
- Late TTS callbacks are ignored unless their utterance ID is still active. Runtime callback ordering and stop latency remain unverified.
- No Build, compilation, tests, CI, APK or physical-device verification was run.

## 52. AVA MOBILE — TTS initialization and Persian capability guard (2026-11-17)

- Added an explicit TTS readiness flag. The engine is not considered ready until initialization succeeds and Android reports Persian language support.
- Engine initialization failure, missing/unsupported Persian voice data, configuration exceptions and utterance playback errors now surface an ERROR state and a user-facing explanation.
- Playback is blocked while TTS is unavailable; Activity destruction clears readiness before shutting down the engine.
- This does not verify installed Persian voice data, audio focus, Bluetooth/audio-route disconnection, vendor engine behavior or real playback. No Build, compilation, tests, CI, APK or physical-device verification was performed.

## 53. AVA MOBILE — Noisy audio route-change stop safeguard (2026-11-17)

- Added a dynamically registered Android `ACTION_AUDIO_BECOMING_NOISY` receiver. During active speaking/listening/processing, the event invokes the shared voice-stop path and informs the user that voice activity was stopped to avoid unintended playback after an output-route change.
- Receiver registration uses `RECEIVER_NOT_EXPORTED` on Android 13+ and is released on Activity destruction.
- Coverage is best-effort: not every Bluetooth/device transition necessarily emits this event. Audio focus request/abandon and focus-loss handling are not yet implemented; OEM routing and recognition behavior remain unverified.
- No Build, compilation, tests, CI, APK or physical-device verification was performed.


## 54. AVA MOBILE — Transient audio focus lifecycle (2026-11-17)

- TTS requests transient audio focus before speaking. Android 8.0+ uses AudioFocusRequest with speech/media attributes and a main-thread listener; older supported versions use the legacy focus API.
- Denied focus blocks playback. Permanent/transient focus loss and duck-related focus changes stop the active utterance; focus gain never auto-resumes speech.
- Focus is abandoned on stop, utterance completion, playback error and lifecycle interruption.
- Scope is TTS output only. OEM focus arbitration, recognizer capture focus and real-device behavior remain unverified.
- Source-only change; no Build, compilation, tests, CI, APK or physical-device verification was performed.


## 55. AVA MOBILE — Speech recognizer session cleanup and callback lifecycle (2026-11-17)

- Centralized guarded release for recognizer completion, errors and startup failures, checking recognizer identity and generation before changing the active session.
- Invalidates the active recognizer reference and generation before platform destruction/cancellation, preventing stale callbacks from updating voice state or applying old recognized text.
- Listener registration and `startListening()` are protected by exception handling; failed startup attempts cancel/destroy the recognizer and report an error.
- Results are ignored when the Activity is finishing or destroyed. Existing `onPause()` behavior continues to stop voice capture/output when backgrounded.
- Source-only safeguard. Permission-dialog behavior, OEM recognition service behavior, audio routing and timing remain unverified. No Build, compilation, tests, CI, APK or device test was run.


## 56. AVA MOBILE — Speech-service error recovery policy (2026-11-17)

- Expanded Persian recovery guidance for audio input, microphone permission, network failures/timeouts, no-match, speech timeout, recognizer-busy, client and server errors.
- Failed recognition sessions remain closed. Recovery is manual through the microphone control; the app does not automatically reopen microphone capture or automatically resubmit recognized text after an error.
- This reduces retry loops and unintended capture/command repetition. No watchdog or guarantee of vendor service callback delivery is claimed.
- Source-only change. No Build, compilation, tests, CI, APK or physical-device verification was performed.


## 57. AVA MOBILE — Bounded speech-session watchdog (2026-11-17)

- Added a generation-bound main-thread watchdog: 45 seconds for speech capture and 20 seconds for final processing after end-of-speech.
- On timeout, only the matching active recognizer is cancelled/released; its generation is invalidated, voice state becomes ERROR and the user is offered manual retry. No automatic microphone restart or command replay occurs.
- Timeout callbacks are removed on result, service error, explicit stop, new session, lifecycle stop and recognizer release.
- Values are provisional safety bounds, not verified latency/service guarantees. Device/OEM behavior and usability remain untested.
- Source-only change. No Build, compilation, tests, CI, APK or physical-device verification was performed.


## 58. AVA MOBILE — Recognition session page binding and navigation interruption (2026-11-17)

- Bound each active recognizer to its originating page and accept final text only while the current page and session origin still match.
- Navigating away from the page during active recognition uses the shared stop path before replacing page content; watchdog and recognizer are invalidated and late callbacks are discarded.
- Returning to chat never resumes or reuses an old microphone session. A new capture requires a fresh explicit user action.
- Source-only safeguard. Android/OEM callback timing, permission-dialog navigation and actual microphone shutdown remain unverified. No Build, compilation, tests, CI, APK or device verification was performed.


## 59. AVA MOBILE — TTS playback page-boundary and delayed auto-speak safeguard (2026-11-17)

- Leaving a page stops active TTS playback via the shared voice stop path, invalidating utterance callbacks before requesting Android playback stop.
- Added a page-generation token so delayed auto-speak from a prior chat view is suppressed even if the user navigates away and returns before the response arrives.
- Auto-speak requires the original chat page generation, current chat destination, non-public mode and existing preference. Late replies are not spoken unexpectedly after navigation.
- Source-only safeguard. TTS engine callback ordering, audio focus and physical output stop latency remain unverified. No Build, compilation, tests, CI, APK or device verification was performed.


## 60. AVA MOBILE — Voice operation mutual-exclusion review and TTS readiness ordering (2026-11-17)

- Reviewed microphone/TTS handoff: `startSpeechInput()` stops active TTS before creating a recognizer; `speakAva()` stops and invalidates active recognition before requesting audio focus and playback.
- Fixed an early-return gap in `speakAva()`: the shared stop path now runs before the TTS readiness check, so an unavailable Persian voice engine cannot leave a previous microphone session active after a playback request.
- Existing generation/identity guards, explicit stop, navigation interruption, Activity lifecycle stop, audio-focus loss and best-effort noisy-route handling remain in force.
- Boundaries: Android service cancellation/destruction is best-effort and does not prove physical microphone shutdown timing. No acoustic echo cancellation, guaranteed prevention of self-capture, exhaustive Bluetooth/device-disconnect detection, or automatic service reconnection is claimed. Failed recognition remains manual-retry; no automatic mic reopen or speech replay is added.
- Source/static review only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## 61. AVA MOBILE — Audio-device route changes and speech-service disconnect handling (2026-11-17)

- Added a lifecycle-managed Android `AudioDeviceCallback` (API 23+) to observe device additions/removals. If voice input or output is active during a device-set change, the shared stop path invalidates the session and requires a fresh explicit user action.
- Kept `ACTION_AUDIO_BECOMING_NOISY` as a complementary best-effort event. Both receiver and callback are unregistered when the Activity is destroyed.
- Added explicit Android 12+ `SpeechRecognizer.ERROR_SERVER_DISCONNECTED` messaging. The existing guarded error path closes the matching recognizer; it never auto-reopens the microphone or replays speech.
- Boundaries: device add/remove callbacks do not expose every Bluetooth profile or routing-policy transition; OEM callback timing requires device tests. No universal SpeechRecognizer/TTS service reconnection is claimed. TTS is not automatically rebound/reinitialized, and playback is not replayed. Physical capture shutdown, acoustic echo cancellation and self-capture immunity remain unverified.
- Source/static review only. No Build, Gradle compilation, tests, CI, APK generation or device verification was performed.


## 62. AVA MOBILE — Explicit TextToSpeech engine recovery (2026-11-17)

- Added a manual voice-settings action to replace and reinitialize the TTS engine, recheck Persian support, and reapply saved voice settings without auto-playing speech.
- Added TTS engine generation and identity guards so callbacks from replaced or destroyed engines cannot mark the current engine ready or mutate its voice state. Stale engines are shut down.
- A failed utterance now releases audio focus and ends that utterance without assuming the entire TTS service is disconnected; the user may explicitly retry engine initialization.
- No microphone auto-open, automatic speech replay, background retry loop, or universal service-reconnection guarantee is introduced.
- Source/static review only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## 63. AVA MOBILE — Voice settings integrity and live voice catalog (2026-11-17)

- Disabled the Persian voice picker while the TTS engine is unavailable; refreshes the settings page after a successful owner-initiated retry so the actual installed Persian voices are shown.
- When the engine is unavailable, allows saving rate, pitch and auto-speak preferences for next initialization without deleting or overwriting the saved voice selection.
- Additional safeguard: if the engine is ready but exposes no Persian voice catalog, applying rate/pitch leaves the stored voice selection untouched.
- When the engine is ready, validates speech rate, pitch and voice/language setter return values before saving preferences. On rejection, attempts to restore prior engine settings and keeps the previous stored preferences.
- Validates persisted voice settings during TTS initialization before setting `ttsReady`. A missing saved voice falls back to the default Persian language voice and is explicitly reported on manual retry.
- Source/static review only; no Build, compilation, tests, CI, APK or physical-device verification.
