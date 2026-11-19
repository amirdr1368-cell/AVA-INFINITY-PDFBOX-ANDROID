# AVA MOBILE — GLASS UI 2.0 Implementation Status

**Date:** 2026-11-17  
**Branch:** implementation/glass-ui-2.0-approved  
**Base:** design/glass-ui-2.0-spec  
**Status:** UI implementation in progress; owner-approved design baseline; not complete or verified.

## Design source of truth

- Canonical detailed design: docs/AVA-GLASS-UI-2.0-DESIGN-SPEC.md
- Owner-approved design Draft PR: [#4](https://github.com/amirdr1368-cell/AVA-INFINITY-ANDROID-V2/pull/4), based on implementation/core-completion.
- Source implementation Draft PR: [#6](https://github.com/amirdr1368-cell/AVA-INFINITY-ANDROID-V2/pull/6), based on this approved design branch.
- Earlier design draft docs/AVA-MOBILE-GLASS-UI-2.0-SPEC.md is retained as historical context and marked superseded.
- The approved palette direction remains Sultan lavender/pink with rose-gold accents on a dark plum background.

## Implemented in source on this branch

- Added AvaGlassTheme as a central source for palette tokens, dark/light gradients, glass surfaces, selected navigation surfaces, primary buttons and destructive button surfaces.
- Added AvaGlassComponents to centralize native Android View construction for text, cards, buttons and rounded surfaces.
- Updated MainActivity to delegate shared component construction to the new presentation helpers.
- Updated the app shell to use centralized GLASS theme tokens and 16 dp content gutters.
- Added a current-destination label to the top bar.
- Refined the four primary destinations to Home, Conversation, Memory and AVA hub.
- Added an AVA hub with links to identity/personality/appearance, voice/language, AVA SECRET, Daily Assistant, Project Console and Security. Memory remains its own primary destination.
- Kept the AVA bottom-navigation destination selected while navigating within its child modules.
- Added in-app navigation history and back behavior.
- Added 48 dp minimum button targets, accessible button descriptions and a distinct destructive action surface.
- Replaced the static Home "ready" message with the actual voice-state label and synchronized it while the Home page is visible.
- Removed the previous blue-toned palette option; night/lavender is now used for that preset.
- Added empty-state guidance to Daily Assistant, AVA SECRET and Memory.
- Made the chat composer multiline/resizable, set adjust-resize keyboard behavior and preserved unsent draft text across ordinary navigation; drafts clear on privacy/session resets.
- Added an explicit confirmation before deleting a Daily Assistant task.

## Not yet complete

- Screen builders are still substantially hosted in MainActivity; further extraction into focused screen files is pending.
- Full consistent component application across every page remains in progress.
- Conversation composer source now supports multiline input, bounded height, resize-aware keyboard behavior, a shared IME/button send path and draft clearing at privacy/session boundaries; runtime/inset behavior still needs device verification.
- Empty, loading, error, locked and permission-denied states need a page-by-page acceptance pass.
- TalkBack, RTL edge cases, font scaling and contrast need a full static review.
- Some older controls still require review to ensure visual state matches the actual backend state.
- AVA hub is an owner/control destination under current access gates; guest/public-only navigation must continue to be checked against AACS.

## Verification boundary

No Build, Gradle compilation, tests, CI, APK generation, or physical-device verification has been run. Static source scans are not compiler or runtime verification. PR #2 and PR #3 remain Draft core/UI baselines; PR #4 is the approved design Draft and PR #6 is the UI implementation Draft. None has been merged by this UI work.


## Owner-approved structure and privacy refinements

- The four primary destinations are fixed as خانه، گفتگو، حافظه، آوا. The AVA hub is an owner/control destination and is not a profile-only tab.
- Public-only mode uses a generic greeting on Home and Conversation; it does not display the owner's name in those contexts.
- Home links to the AVA hub instead of duplicating the hub's module list. Memory remains a primary tab and is not duplicated as a hub tile.
- Hub tiles expose one accessible action to TalkBack rather than separate decorative child elements.
- This is source implementation only. No Build, Gradle compilation, tests, CI, APK or physical-device verification has been run.


## Static contrast refinement — 2026-11-17

- Dark primary action gradients were deepened so white action labels meet a stronger text-contrast target while retaining lavender/rose undertones.
- Light-theme accent text was darkened and selected navigation text now uses a high-contrast text token in both themes.
- This is a source-token review only; no screenshot-based or device contrast verification was performed.


## Conversation composer interaction refinement — 2026-11-17

- The send action is now disabled until the draft contains non-whitespace text and exposes a clear TalkBack label.
- The keyboard SEND action and the visible send control use the same message-submission path.
- The multiline draft remains bounded to four visible lines and retains the existing privacy/session reset clearing behavior.
- No Build, compilation, tests, CI, APK or physical-device verification was performed.


## Explicit chat microphone affordance - 2026-11-17

- Added a dedicated microphone action beside the multiline chat composer.
- The action starts the existing explicit speech-recognition flow; no always-on listening is introduced.
- Added an accessible label for the input field and the microphone action.
- No Build, Gradle compilation, tests, CI, APK or physical-device verification was performed.


## Source review and privacy/accessibility fixes — 2026-11-17

- Public-only mode now hides the owner's avatar in the top bar and Home hero; avatar visibility is re-evaluated whenever the image is refreshed.
- Bottom-navigation TalkBack descriptions now announce the selected destination.
- Navigation rejects unknown destination keys before mutating the page or history stack.
- Added a conversation-generation guard. When privacy mode or a session boundary clears the conversation, late callbacks from older in-flight requests are discarded instead of repopulating the cleared conversation or triggering follow-on speech.
- The external request itself cannot be recalled after transmission; this guard only prevents stale results from being applied locally after a boundary change.
- Static delimiter checks passed for the four touched Kotlin source files. Focused source checks confirmed the privacy-avatar guards, selected navigation announcements, unknown-destination guard, stale-response guard and eight invalidation sites.
- These checks do not establish Kotlin compilation, runtime correctness, or full security validation.

## Remaining review work

- Extract Home, Conversation and Memory screen builders from MainActivity into focused classes without changing their service/policy behavior.
- Complete page-by-page loading, empty, error, restricted and permission-denied state coverage.
- Review keyboard/inset behavior, TalkBack traversal, RTL, font scaling and contrast on a real device.
- Verify lifecycle behavior for microphone/TTS and navigation/back stack.
- Audit every UI control against the authoritative service state and owner/session permissions.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification has been run.

## Memory deletion safeguard — 2026-11-17

- Memory-note deletion now requires an explicit confirmation dialog that explains the selected memory space is affected and that the action is not reversible from the page.
- Immediately before deletion, the UI rechecks whether the current session still matches the selected person and retains PERSONAL access and relationship-memory approval. If the session boundary or access changed, deletion is blocked.
- This is a source-level safeguard only. It does not establish storage-layer transactional guarantees or verified secure erasure.
- Static focused source checks and delimiter-balance checks passed. No Build, compilation, tests, CI, APK or device verification was run.

## Voice lifecycle and microphone permission safeguard — 2026-11-17

- Microphone permission requests now record the page that initiated the request. A later grant starts recognition only if the permission is still granted, the app remains on that same page, and public-only mode is not active.
- A denied, stale, or context-mismatched permission result does not start microphone capture.
- `onPause()` invalidates any pending microphone permission continuation and stops active speech recognition and TTS output so voice capture/output does not continue while the app is backgrounded.
- Existing recognizer identity/generation guards continue to ignore callbacks from a stopped or superseded recognizer.
- This is source-level lifecycle hardening only. Android permission-dialog lifecycle behavior, OEM behavior, microphone timing, TTS interruption and actual audio routing remain unverified on device.
- Focused static source checks and delimiter-balance checks passed. No Build, compilation, tests, CI, APK or device verification was run.


## Voice input/output mutual-exclusion safeguard — 2026-11-17

- TTS playback now first routes through the shared voice-stop path, cancelling/destroying any active speech recognizer and invalidating its generation before output begins.
- This prevents the app from intentionally running microphone recognition and TTS playback concurrently; it does not guarantee Android/OEM audio-focus behavior or a measured stop-latency target.
- TTS interruption timing, P95 latency, noisy/multi-speaker behavior and audio routing remain runtime acceptance items.
- No Build, compilation, tests, CI, APK or physical-device verification was run.


## TTS stop callback ordering safeguard — 2026-11-17

- `stopAvaVoice()` now invalidates the utterance identifier and recognizer generation and publishes the IDLE state before calling Android TTS stop or recognizer cancel/destroy.
- Late or queued TTS callbacks therefore fail the active-utterance identity check and cannot restore SPEAKING after a user stop.
- This is a source-level ordering safeguard. Android engine callback ordering, actual stop latency and audio-device behavior still require runtime verification.
- No Build, compilation, tests, CI, APK or physical-device verification was run.

## TTS initialization and Persian capability safeguard — 2026-11-17

- TTS initialization now remains unavailable until Android reports successful engine initialization and the engine reports Persian (`fa-IR`) as supported.
- Initialization failure, missing/unsupported Persian voice data, configuration exceptions and utterance playback errors now leave the voice state in ERROR with a user-facing explanation instead of presenting a false ready/idle state.
- `speakAva()` refuses to start playback until the TTS engine has passed the readiness check; destruction clears readiness before engine shutdown.
- This is source-level handling only. Installed voice-data availability, vendor engine behavior, audio-route changes, Bluetooth disconnection, audio focus and actual playback remain unverified on device.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.

## Audio output route-change safeguard — 2026-11-17

- Added a lifecycle-managed receiver for Android's `ACTION_AUDIO_BECOMING_NOISY` event. If active voice input/output is in progress when the system reports an audio route becoming noisy (commonly headset unplug/disconnect), the shared stop path invalidates active callbacks and stops voice activity.
- The receiver is registered with a non-exported flag on Android 13+ and unregistered during Activity destruction; older supported Android versions use the compatible registration overload.
- This event is best-effort and does not detect every Bluetooth/audio-device transition. Audio focus ownership, focus-loss callbacks, vendor routing behavior and recognition continuity remain unimplemented or unverified; no claim is made that every audio disconnection is detected.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## Transient audio focus lifecycle — 2026-11-17

- TTS now requests transient audio focus before playback, using speech/media audio attributes and a main-thread focus listener on Android 8.0+; supported older Android versions use the legacy focus API.
- If focus is denied, speech playback does not start and the user receives an explanation.
- Permanent loss, transient loss and duck-related focus changes stop the active utterance through the shared stop path. Ava does not automatically resume when focus returns.
- Focus is abandoned on explicit stop, utterance completion, playback error, focus loss and Activity pause/destruction through the shared lifecycle.
- This handling currently governs TTS output; it does not claim ownership of speech-recognition capture focus or guarantee every OEM/audio-device behavior. Runtime focus arbitration and callback behavior remain unverified.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## Speech recognizer session cleanup and callback lifecycle — 2026-11-17

- Added a single guarded release helper for recognizer completion, recognition errors and startup failures. It verifies both recognizer identity and generation before releasing the active session.
- The active recognizer reference and generation are invalidated before platform destruction/cancellation, so late callbacks from an obsolete session cannot update the current voice state or inject stale recognized text.
- Listener registration and `startListening()` now share guarded exception handling; startup failure attempts cancellation and destruction and reports an error.
- Recognition results are not applied after the Activity is finishing or destroyed. Existing `onPause()` stop behavior remains the lifecycle boundary for backgrounding.
- This is source-level lifecycle hardening only. Microphone permission prompts, recognition-service callbacks, OEM behavior, audio routing and timing remain unverified on device.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## Speech-service error recovery policy — 2026-11-17

- Expanded user-facing guidance for microphone/audio input, permission, network, timeout, no-match, recognizer-busy, client and server errors.
- Recovery remains explicitly user initiated: the failed recognizer session is released, and Ava never automatically reopens the microphone or resubmits recognized text after an error. The user may retry by tapping the microphone control after addressing the displayed cause.
- This avoids retry loops, unexpected microphone activation and accidental repeated commands. It does not add a service watchdog or prove that every vendor recognition service will return a callback.
- Static source inspection only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## Bounded speech-session watchdog — 2026-11-17

- Added a main-thread, generation-bound timeout for active speech recognition: 45 seconds for the listening phase and 20 seconds after end-of-speech while awaiting final processing/results.
- If the active session exceeds its phase limit without a terminal callback, the watchdog cancels and releases only that exact recognizer session, invalidates its generation through the shared release path, sets ERROR and asks the user to retry manually if desired.
- The timeout is cleared on normal results/errors, explicit stop, replacement session, lifecycle interruption and recognizer release. It never restarts capture or repeats a command.
- Timing values are initial safeguards, not measured performance targets; device/OEM callback latency and usability require later acceptance testing.
- Source/static review only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## Recognition session page binding and navigation interruption — 2026-11-17

- Each speech-recognition session now records the exact page that initiated it. A permission grant remains page-bound, and the recognizer result is accepted only while both the current page and session origin still match.
- Navigating to a different page while recognition is active invokes the shared stop path before replacing the page content. This cancels capture, clears the watchdog, invalidates the generation and destroys the recognizer.
- A late result from an obsolete or mismatched page is discarded and cannot populate the conversation composer. Returning to chat does not revive or reuse the previous recognition session; the user must start a new session explicitly.
- The originating-page marker is cleared on explicit stop and guarded recognizer release. This is source-level protection; Android callback timing, navigation behavior under OEM permission dialogs and microphone shutdown latency remain unverified on device.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## TTS playback page-boundary and delayed auto-speak safeguard — 2026-11-17

- Navigating away from a page now stops active TTS playback through the shared voice stop path, in addition to stopping an active speech recognizer. Utterance identity is invalidated before the Android stop request, so late TTS callbacks cannot restore a stale SPEAKING state.
- Added a page-generation counter. Each actual page transition invalidates pending auto-speak eligibility, including the case where the user leaves chat and returns before a delayed conversation response arrives.
- Automatic reading of a response now requires the same chat-page generation that initiated the request, active chat page, non-public mode and the existing auto-speak preference. A late response may still be retained in conversation history, but it is not spoken after the user has navigated away or returned through a new page instance.
- Manual TTS remains explicitly stoppable and is stopped on navigation, Activity pause and destruction. This is source-level behavior; TTS engine callbacks, audio focus and actual output shutdown require runtime/device verification.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was performed.


## Voice operation mutual-exclusion review — 2026-11-17

- Reviewed the start/stop paths for speech recognition and TTS. Starting microphone recognition first invokes the shared stop path, which invalidates any active TTS utterance and stops output; starting TTS likewise invalidates and releases an active recognizer before requesting audio focus and playback.
- Corrected one ordering gap: a TTS request now stops any active recognition session before checking whether the Persian TTS engine is ready. Previously, an unavailable/not-yet-ready TTS engine could return early while a microphone session remained active.
- Generation and identity guards remain in place for recognizer and utterance callbacks. Navigation, Activity pause/destruction, explicit stop, and the noisy-route event continue to use the shared stop path.
- Limitations: Android's cancel/destroy and TTS stop calls are best-effort platform requests; source code cannot prove the microphone capture pipeline has physically shut down before output begins. No acoustic echo cancellation, speaker-output self-capture immunity, complete Bluetooth/device-disconnect detection, or service reconnection guarantee is claimed. Recognition-service failures remain user-retry only; TTS readiness/error states are reported without automatic retries.
- Static source review only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## Audio device route-change and speech-service disconnect safeguards — 2026-11-17

- Added an Activity-lifecycle-managed `AudioDeviceCallback` on Android 6.0+ to observe audio-device additions and removals. If recognition or TTS is active when the available audio-device set changes, the app invokes the shared stop path, invalidates active callbacks and asks the user to initiate any new voice session manually.
- The callback is registered during Activity creation and unregistered during destruction. Existing `ACTION_AUDIO_BECOMING_NOISY` handling remains as a complementary best-effort signal.
- Added explicit guidance for Android 12+ `SpeechRecognizer.ERROR_SERVER_DISCONNECTED`. The exact recognizer session is released by the existing guarded error path; microphone capture is never automatically reopened and recognized commands are never replayed.
- Limitations: device callbacks report device additions/removals, not every routing-policy change or Bluetooth profile transition; callback timing and vendor behavior require device testing. Public SpeechRecognizer APIs expose recognition-service errors but do not provide a universal service reconnection guarantee. TextToSpeech binder/service recovery is not automatically re-created; initialization and utterance failures remain visible as errors, and no automatic replay is attempted. The app cannot guarantee physical microphone shutdown latency, echo cancellation or immunity from acoustic self-capture.
- Static source inspection only. No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## TTS explicit engine recovery — 2026-11-17

- Added an owner-initiated `راه‌اندازی دوباره موتور گفتار` control to voice settings. It shuts down the previous TTS instance, creates a new instance, and repeats engine initialization plus Persian (`fa-IR`) capability and saved voice/rate/pitch configuration checks.
- Added a monotonically increasing TTS initialization generation and engine-identity checks. Late initialization and utterance callbacks from replaced or destroyed engines are ignored; stale engines are shut down rather than allowed to mark the current engine ready or change playback state.
- A single utterance `onError` now ends that utterance and releases audio focus without automatically declaring the whole engine disconnected. The user can explicitly request engine reinitialization if later playback continues to fail.
- Recovery never opens the microphone, starts playback, or replays prior speech automatically. The user must separately choose the sample playback or another explicit speech action.
- This is source-level handling only. Vendor TTS service reconnection behavior, asynchronous callback timing, Persian voice data and actual playback require later device verification. Android exposes no universal TextToSpeech service-disconnect callback, so no universal reconnection guarantee is claimed.
- No Build, Gradle compilation, tests, CI, APK generation or physical-device verification was run.


## Voice settings application integrity — 2026-11-17

- The Persian voice picker is disabled while TTS is unavailable and is refreshed when an owner-initiated engine retry succeeds while the voice settings page remains open. This prevents a stale empty list from masquerading as the available voice catalog.
- When the engine is unavailable, rate, pitch and auto-speak preferences may still be saved for the next initialization; the previously selected voice name is preserved and the UI explicitly says voice selection has not changed.
- Additional safeguard: if the engine reports ready but the Persian voice catalog is empty, applying rate/pitch does not reset the saved voice preference or force a default voice change.
- When TTS is ready, rate, pitch and voice/language setters are checked before preferences are committed. If the engine rejects any setting, the code attempts to restore the previously saved engine values and does not overwrite saved preferences.
- Engine initialization now checks the return statuses for persisted rate, pitch and voice/language configuration before marking TTS ready. If a saved Persian voice no longer exists, the default Persian voice is used and the owner is informed during an explicit retry; the stale saved name is not silently represented as active.
- Source-level checks only. No Kotlin compilation, Build, Gradle tests, CI, APK generation or device verification was run. Runtime return codes and vendor-specific voice availability remain to be verified on hardware.
