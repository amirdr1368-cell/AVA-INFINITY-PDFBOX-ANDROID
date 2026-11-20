# AVA INFINITY MOBILE — ACL & Implementation Roadmap

**Document:** ACL-MASTER-ROADMAP-01  
**Version:** 1.0  
**Status:** Design decisions approved; incremental core source implementation in progress; build and device verification pending.  
**Platform:** Android mobile application  
**Owner / final decision authority:** Amir Darbandi Pour

## 1. Project direction approved by Amir

The project will prioritize completing and implementing already-approved capabilities before opening additional feature-design tracks.

The agreed sequence is:

1. Consolidate the approved module specifications into complete, versioned engineering documentation.
2. Map dependencies, data ownership, permission boundaries, failure behavior, and acceptance criteria.
3. Run a cross-module consistency and implementation-readiness review. Resolve gaps before coding.
4. Implement ready capabilities incrementally, with tests at each stage.
5. Verify builds and behavior on target Android devices. Keep design approval, implementation, build verification, and device testing as separate statuses.
6. Resume the Glass UI 2.0 detailed design/implementation track after the approved core capabilities have been prioritized and stabilized, unless a dependency requires earlier UI work.

No feature may bypass the design-first gate. No implementation-readiness approval is implied by this roadmap entry.

## 2. ACL — AVA Communication Language

All eight ACL submodules and ACL-R01 through ACL-R08 design audits have been approved by Amir. This is design approval only; no implementation or device verification is claimed.

| ID | Area | Approved behavior |
|---|---|---|
| ACL-01 | Base language | Persian is the default. Ava changes language when Amir explicitly selects it or begins conversing in that language, and follows the selected language until changed or context clearly calls for switching. Preserve established forms of address and vocabulary. |
| ACL-02 | Tone and personality | Warm and balanced: natural, friendly, respectful, independent; neither excessively emotional/dependent nor cold/formal. Use humor appropriately, handle sensitive matters seriously, disagree respectfully when needed, and avoid manipulation, guilt, degradation, or pretending to have human feelings. |
| ACL-03 | Emotional expression | Adapt support to conversational context and known preferences: listening, empathy, suggestions, lightening the mood, or giving space. Do not claim certainty about inferred emotions. Ask briefly when ambiguous and respect explicit support requests. |
| ACL-04 | Audience adaptation | Adapt conversational style to another person during the current session. Persist preferences in that person's PRP profile only with Amir's approval. Keep profiles isolated; familiarity never grants private access or changes AACS permissions. Apply consent requirements to personal information/photos and preserve prior profile versions for audit/rollback. |
| ACL-05 | Conversational intelligence | High confidence: answer directly. Medium: state the likely interpretation and answer cautiously. Low: ask a concise clarifying question. Consider relevant context, apply additional caution to sensitive matters, and do not fabricate confidence percentages. |
| ACL-06 | Conversation management | Occasionally suggest resuming unfinished topics without intrusive repetition. Respect declines and personal space. Keep private topic titles off lock-screen notifications. External notifications require separate opt-in. Long-term topics belong in appropriate project memory, not indefinite temporary retention. |
| ACL-07 | Voice and interruption | Provide a direct stop control; yield promptly when Amir intentionally interrupts; reduce false interruption from ambient noise; preserve turn context and allow continuation on request; expose listening/processing/speaking states; handle microphone, audio-disconnection, and Android permission errors. Voice input is limited to explicit voice-chat and Android permission context; no always-on listening. |
| ACL-08 | AVA SECRET vocabulary | New terms begin as drafts. Only Amir approves publication to the authoritative vocabulary. Entries support meaning, usage, category, optional pronunciation, multilingual terms/equivalents, dated version history, rollback, controlled add/edit/approve permissions, and duplicate/conflict checks. Historical conversation meaning must not silently change after edits/deletions. |

## 3. ACL engineering audit decisions

- **ACL-R01:** Language switching is governed by ACL-01; preserve the approved default-language behavior.
- **ACL-R02:** Preserve previously defined forms of address and phrases; do not redesign them.
- **ACL-R03:** Inferred emotional state is temporary conversational context, not a persistent profile attribute. No medical/psychological diagnosis or labeling. Raw emotional content is not exposed in security logs. Persistent emotional preferences require Amir's approval.
- **ACL-R04:** Audience adaptation is session-scoped by default. Clear temporary recognition at session end unless separate authorized retention applies. Persistent preferences require Amir's approval. Profiles remain isolated; familiarity does not elevate AACS permissions; required consent applies to personal information/photos; preserve prior versions.
- **ACL-R05:** Use high/medium/low confidence handling as defined in ACL-05; do not invent percentages.
- **ACL-R06:** Resume-topic suggestions must be non-intrusive. Respect disable/delete/defer/complete choices. External notifications require separate opt-in. Hide private topic titles on lock screens. Do not retain temporary memory indefinitely.
- **ACL-R07:** Stop button target <100 ms; pause after valid speech detection target <300 ms. These are engineering targets, not measured results. Measure P95 latency and test ambient noise, music, television, and multiple speakers. Do not assume speaker identity when unreliable. No always-on listening and no microphone/camera command concepts in AVA SECRET.
- **ACL-R08:** Vocabulary governance and versioning rules are defined in ACL-08; Amir retains final publication approval.

## 4. Dependencies to document and verify

| Component | ACL integration boundary |
|---|---|
| PRP | Per-person persistent conversational preferences; approval and version history. |
| AACS | Authorization remains independent of familiarity, tone, or audience adaptation. |
| AEMS | Retention, encrypted storage, backup, restore, and migration behavior for permitted data. |
| AVA SECRET | Authoritative vocabulary, draft/approval workflow, multilingual equivalents, versioning, rollback. |
| Android voice layer | Explicit voice-chat lifecycle, runtime permissions, audio focus, interruption, playback stop, and error handling. |
| Conversation engine | Context handling, turn ownership, ambiguity/confidence policy, and response continuation. |

These are identified integration dependencies, not a claim that interfaces or contracts have already been implemented.

## 5. Acceptance and readiness gates

Before implementation of an ACL capability:

- Complete the detailed functional and technical specification.
- Define inputs, outputs, state transitions, permissions, data classification, retention, and failure/recovery behavior.
- Define unit, integration, security/privacy, and device acceptance tests.
- Resolve conflicts with PRP, AACS, AEMS, AVA SECRET, and Android platform behavior.
- Document measurable targets and distinguish targets from verified measurements.
- Register the final version in the project documentation/central control index.
- Obtain explicit implementation-readiness approval from Amir.

For ACL voice behavior, test latency at P95 as well as typical behavior; include noisy and multi-speaker environments. The <100 ms and <300 ms values remain targets until measured.

## 6. Current status

| Work item | Status |
|---|---|
| ACL-01 through ACL-08 design decisions | Approved |
| ACL-R01 through ACL-R08 design audits | Closed / approved |
| Consolidated engineering documentation | This roadmap entry created; detailed module specifications still need consolidation |
| Dependency contracts and acceptance criteria | Pending |
| Central project index registration | Pending |
| Final cross-module readiness review | Pending |
| Implementation-readiness approval | Pending |
| ACL implementation and tests | ACL-01 and ACL-08 source work recorded; initial ACL-02/03/04 source slices added; JUnit test sources added but not executed |
| Glass UI 2.0 detailed work | Deferred behind the approved-core implementation priority, subject to dependency review |

## 7. Change control

This document records Amir's approved direction and the current design status. Any change to approved behavior must be proposed, versioned, reviewed for cross-module impact, and approved by Amir before it is treated as authoritative. A documentation commit does not itself authorize coding, merging, release, or deployment.


## 8. Incremental ACL implementation update - 2026-11-17

This update records implementation progress only. It does not close any module's acceptance gate.

| Module | Current source status | Remaining gate |
|---|---|---|
| ACL-01 | Persian-default language selection, explicit switching, script heuristics, encrypted persistence, and provider language context are present. | Broader language coverage, integration review, and unexecuted tests. |
| ACL-02 | Tone settings are mapped to bounded local-response and provider guidance. Serious subjects remain calm even when the selected style is lively. | Full conversation acceptance review, edge cases, and unexecuted tests. |
| ACL-03 | Stateless per-turn support policy recognizes explicit listening, suggestions, humor, and space requests; tentative distress and ambiguous requests are handled without diagnosis or certainty claims. | Broader context/language coverage, acceptance review, and unexecuted tests. |
| ACL-04 | Active approved Level 2/3 profile can provide bounded style/length/emoji preferences to the current session. Stable preference expressions produce a proposal response only; no automatic profile write occurs. | Profile UI acceptance, session-transition review, integration review, and unexecuted tests. |
| ACL-05 | Initial conservative ambiguity/correction/figurative-language policy is wired into local and provider paths; no numeric confidence score is generated. | Broader ambiguity coverage, acceptance review, and unexecuted tests. |
| ACL-06 | Initial memory-only, owner-context in-conversation follow-up controller integrated; decline/completion and session/privacy cleanup are covered in source. | Broader UI acceptance and unexecuted tests; no scheduled notification feature. |
| ACL-07 | Manual stop, explicit mic-start handoff, TTS/recognizer state indicators, and speech permission/service error handling added. | Automatic speaker-verified interruption, ambient-noise discrimination, P95 latency/noise tests, and acceptance review remain open. |
| ACL-08 | Draft-first vocabulary governance, versioning, owner approval, rollback, archive/tombstone, multilingual equivalents, and conflict checks are present in source. | UI acceptance review, integration review, and unexecuted tests. |

ACL-04 privacy contract:
- Audience adaptation is available only when an active Level 2/3 session is approved for PERSONAL access and the stored profile ID and level exactly match that session.
- Public-only, owner, public guest, unapproved, mismatched, or missing-profile contexts receive no person-profile adaptation.
- Only allowlisted style signals from speakingStyle and preferences are mapped to bounded enums. Raw profile strings, relationship history, intimacy, occupation, interests, and privacy restrictions are not copied into model instructions.
- Audience adaptation changes wording only. It never changes AACS permissions or enables private/control access.
- A stable preference expressed during a person session is offered as a proposal for owner review. It is not saved, logged as a profile change, or made permanent.
- Audience context is derived again per request and is not persisted beyond the active session.

Verification boundary: source-level checks only. JUnit tests are present but not executed. No Gradle Build, APK, CI, or physical-device test was performed.


## 9. ACL-05 confidence handling - 2026-11-17

- Clear requests are guided toward direct answers without unnecessary hedging.
- Narrow consequential ambiguous references are handled conservatively: without a recent assistant antecedent, ask one short clarification; with recent context, state the likely interpretation tentatively and invite correction before consequential or irreversible action.
- Explicit corrections trigger acknowledgement and revision without defensiveness.
- Explicit humor/irony markers trigger context-aware interpretation. Laughter markers alone do not force a figurative reading.
- No numeric confidence percentage or fabricated certainty is generated by the policy.
- Added source tests for direct requests, consequential ambiguity with/without context, corrections, humor, and laughter-only cases. Tests are not executed.
- This is a bounded heuristic policy, not a calibrated semantic confidence model. Full acceptance and integration review remain pending.


## 10. ACL-06 unfinished-topic follow-up - 2026-11-17

- Explicit deferral phrases can hold the preceding user topic in an Activity-lifetime in-memory controller.
- A single in-conversation suggestion may appear when the owner next opens chat or explicitly returns to continuation. The same pending topic is not repeatedly suggested.
- A clear refusal, disinterest, or completion phrase clears the pending topic.
- Follow-up state is cleared on public-only activation, owner/session transitions, and Activity destruction.
- The controller is owner-context only. It clears rather than retaining topics in non-owner or public-only contexts.
- No notification, alarm, WorkManager job, or out-of-app reminder is scheduled. Any future scheduled notification requires a separate explicit consent flow and must not expose private topic text in visible notifications.
- Follow-up state is memory-only and is not written to temporary memories or persistent profile storage. Long-term project decisions remain in project documentation/project memory, not this transient controller.
- Added source tests for single-suggestion behavior, explicit continuation, refusal, completion, missing antecedent, and context-boundary clearing. Tests are not executed.
- This is an initial bounded implementation; full UX/acceptance review remains pending.


## 11. ACL-07 voice interruption foundation - 2026-11-17

- Added visible voice states: IDLE, LISTENING, PROCESSING, SPEAKING, and ERROR.
- Added an immediate user-controlled stop action in chat and voice settings. It stops TTS, cancels/destroys the active recognizer, and does not erase the conversation turn.
- Starting explicit microphone input first stops current TTS output, then starts one foreground recognition session. Recognized text is placed in the composer and is not automatically submitted.
- TTS start/completion/error callbacks update voice state. Android speech-recognition errors and permission/service failures receive specific feedback.
- No always-on listening or automatic AVA SECRET voice-command recognition is enabled.
- Automatic interruption based on speech recognition is deliberately not enabled: the current stack does not verify that detected speech is Amir rather than TTS echo or ambient speech. Cutting off speech based on an unverified signal would violate the approved conservative rule.
- Stop-button <100 ms and valid-speech interruption <300 ms remain targets, not measured results. No P95 latency, speaker-discrimination, or noisy-environment testing was performed.
- This is a partial implementation. Automatic speaker-verified interruption and acceptance validation remain open.


## ACL-05 safety refinement - 2026-11-17

- Recent assistant context now means the immediately preceding conversation item is an assistant response; an older assistant turn separated by another user turn no longer makes an ambiguous reference actionable.
- Ambiguous references to deletion, removal, sending, or changing now enter a separate CONFIRM_ACTION path. The local responder asks for the exact target/action; provider guidance explicitly forbids execution claims or transmission before explicit confirmation.
- Added JUnit source cases for stale context and ambiguous high-impact actions. Tests are not executed.
- This remains a conservative heuristic, not a calibrated confidence model.


## ACL-07 callback lifecycle hardening - 2026-11-17

- TTS progress callbacks are now tied to the active utterance ID; completion/error callbacks from an older utterance cannot overwrite the current voice state after a stop or replacement.
- Speech-recognition callbacks are tied to the active recognizer instance and a monotonically increasing session generation. Results/errors from a cancelled or replaced recognizer are ignored.
- Recognizer instances are cleared/destroyed after terminal results/errors and on explicit stop.
- Explicit microphone start still stops TTS first and stages recognized text in the composer; it does not auto-submit.
- Automatic speaker-verified interruption remains disabled pending reliable discrimination of Amir's speech from ambient sound and TTS echo. Latency/noise/P95 targets remain unmeasured.


## Implementation status checkpoint - 2026-11-17

| ACL | Source implementation status | Still open |
|---|---|---|
| ACL-01 Language | Persian default, explicit switching, persistence and heuristic detection implemented | Broader language-generation quality; local generation beyond Persian/English |
| ACL-02 Tone | Owner-selected tone/length/emoji preferences and audience-aware local/provider guidance implemented | Full conversational acceptance review and broader local response coverage |
| ACL-03 Emotional support | Distress assessment, bounded support modes, practical/space-respecting response guidance implemented | Acceptance review for mixed intent and high-distress edge cases |
| ACL-04 Audience | Approved-session profile style preferences are allowlisted and applied without automatic profile writes | Full audience privacy acceptance review and more test cases |
| ACL-05 Confidence | Qualitative clarification, correction, ambiguity and figurative-language policy implemented | Wider edge-case acceptance; no numeric confidence is to be introduced |
| ACL-06 Follow-up | Memory-only unfinished-topic suggestions with decline/defer/complete controls and session cleanup implemented | Optional external notifications remain separate opt-in future work |
| ACL-07 Voice | Explicit mic handoff, stop controls, voice-state feedback, stale-callback guards and TTS/recognizer error handling implemented | Speaker-verified automatic interruption, ambient-noise/TTS-echo discrimination and P95 latency/noise measurements |
| ACL-08 Vocabulary | Draft-first vocabulary, owner approval, version history, rollback, multilingual equivalents and archive/tombstone behavior implemented in source | UI/acceptance review and runtime verification |

All statuses above refer to source-level implementation only. No Build, JUnit execution, CI, APK generation or physical-device testing has been performed. ACL-07 automatic interruption remains intentionally disabled until reliable speaker discrimination can be demonstrated.
