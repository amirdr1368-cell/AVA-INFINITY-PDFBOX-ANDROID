# AVA GLASS UI 2.0 — Detailed Mobile Design Specification

**Date:** 2026-11-17  
**Scope:** AVA INFINITY Android mobile application only.  
**Status:** Owner-approved navigation and AVA hub structure; source implementation authorized on a separate branch (2026-11-17). Numeric palette tokens remain implementation starting values subject to contrast review.  
**Implementation:** No UI code is changed by this design-only document.

## 1. Identity and design principles

AVA GLASS UI 2.0 is one coherent environment, not a collection of unrelated settings pages. It must feel calm, premium, personal without being intrusive, and immediately readable in Persian.

Preserve the approved identity:
- Sultan lavender with a soft pink undertone.
- Rose-gold highlights and action accents.
- Very dark plum/purple night surfaces as the primary theme.
- Glassmorphism through translucent surfaces, fine luminous edges and layered depth.
- AVA infinity mark as a recurring brand signature.
- Persian-first, right-to-left layout and readable Persian typography.
- Optional light theme; dark theme is the default.

Do not introduce a blue-led palette. Glass is a readable surface first and a visual effect second. Glow, transparency and animation must never compete with content.

## 2. Target device and layout

Primary target: Xiaomi Redmi Note 11, Android 13, 6.43-inch, 1080 × 2400. Design for approximately 360–393 dp content width and nearby Android phones.

- Portrait-first; landscape remains usable.
- 16 dp outer gutter; 12–16 dp card gaps; 8 dp spacing rhythm.
- Interactive targets at least 48 × 48 dp.
- Bottom navigation and chat composer stay clear of system navigation and keyboard.
- One scrollable content column on phone; no fixed-width panels or required horizontal scrolling.
- Support Android font scaling without clipping.
- Keep important actions reachable without precision tapping.

## 3. Visual tokens

These are implementation starting values; tune only to improve contrast and visual balance while preserving the approved palette.

| Token | Value | Usage |
|---|---|---|
| night_950 | #100A18 | Main dark background |
| night_900 | #191022 | Secondary background/navigation |
| plum_800 | #30203D | Deep gradient stop |
| lavender_500 | #A985C8 | Primary lavender |
| lavender_300 | #D8BCE8 | Pink-lavender glow |
| rose_gold_500 | #C58E86 | Main accent/action |
| rose_gold_300 | #E0B6A9 | Soft highlight |
| glass_dark | White 8–14% alpha | Dark glass surfaces |
| glass_light | White 58–76% alpha | Light glass surfaces |
| text_primary_dark | #F7F0FA | Main dark-theme text |
| text_secondary_dark | #C8B9D1 | Secondary dark-theme text |
| text_primary_light | #291D31 | Main light-theme text |

Surface rules:
- Cards: 20–24 dp corners, fine low-opacity border, restrained shadow.
- Primary buttons: rose-gold/lavender accent with high-contrast text.
- Secondary buttons: translucent fill and visible outline.
- Destructive actions: unmistakable warning treatment.
- Selected state uses icon, label and shape as well as color.
- Avoid expensive live blur and constant glow on Redmi Note 11; prefer translucent fills and gradient overlays.
- Never place body text over a bright glow without a readable surface.

## 4. App shell and navigation

Persistent shell:
1. Compact top bar with AVA infinity mark, page title and relevant avatar/status.
2. Central page area with independent vertical scrolling.
3. Persistent four-item bottom navigation.

Keep four destinations to avoid crowding:
1. **خانه** — home and quick actions.
2. **گفتگو** — conversation.
3. **حافظه** — memory and personal data.
4. **آوا** — AVA hub.

The current source already has a four-destination navigation pattern. Refine the fourth destination into an AVA hub rather than a profile-only page.

AVA hub modules:
- هویت و شخصیت آوا
- صدا و زبان
- AVA SECRET
- دستیار روزانه
- کنسول مرکزی
- امنیت و دسترسی
- ظاهر و شخصی‌سازی

Use two columns only when Persian labels and touch targets remain comfortable; otherwise use full-width rows. Each tile has one icon, title, concise status and clear disabled/selected state.

Navigation rules:
- Active destination is obvious.
- Preserve safe scroll/selection state when switching tabs where practical.
- Back returns to the previous in-app destination before leaving the app.
- Authentication and critical confirmation flows cannot be bypassed by tab switching.
- Restricted sessions never reveal owner-only destinations or data.

## 5. Screen design

### 5.1 Home — خانه

Order:
1. Compact brand header.
2. Hero glass card with AVA avatar, AVA INFINITY identity and concise welcome.
3. Presence status: آماده، در حال گوش‌دادن، در حال پردازش، در حال صحبت، متوقف or unavailable.
4. Quick actions: گفتگوی تازه، ورود به صدا (only when available), شخصی‌سازی.
5. Real contextual cards for pending tasks or memory/backup warnings.
6. Memory/Security shortcuts only when the active role permits them.

Tapping the avatar opens AVA identity/profile. No essential action depends on long-press. Missing custom image falls back to official default artwork. Never show a fake ready state or fabricated system data.

### 5.2 Conversation — گفتگو

Structure: title and actual session state; privacy banner when required; scrollable messages; composer anchored above keyboard/system navigation; explicit microphone and send/stop actions.

- AVA bubbles: soft lavender glass; Amir bubbles: contrasting plum/rose surface.
- Comfortable message width, readable timestamps, clear speaker identity beyond color.
- Multiline composer grows to a limit, then scrolls internally.
- Send is enabled only with valid text.
- While speaking, show توقف.
- Microphone is explicit and stateful.
- Listening/processing/speaking indicators appear only when confirmed by the service.
- Keyboard never hides the latest message or composer.
- Preserve unsent text across ordinary page changes.
- Local canned replies must be labelled as limited/local, not as a connected generative engine.
- Distinguish provider, network, permission and empty-response errors.
- No fake typing animation when no response is active.
- Private content never appears in public notifications or lock-screen previews.
- History deletion requires an explicit target and confirmation.

### 5.3 Memory — حافظه

Sections:
- حافظه امیر
- پروفایل اشخاص
- حافظه موقت
- وظایف و پیگیری‌ها
- پشتیبان و سلامت حافظه

Owner-only sections are hidden or visibly locked in non-owner sessions. Guests see only their approved memory bucket. Public-only mode reveals no private content. Locked states explain the required approval without previewing protected content. Temporary memory shows expiry clearly; viewing does not imply renewal.

Backup view distinguishes primary storage, independent destination, verified chain health, last successful backup and restore availability. A file's mere existence does not mean the backup is healthy. Restore is a separate protected flow: point selection, preview, owner re-authentication, final confirmation.

### 5.4 AVA identity/personality — هویت و شخصیت آوا

Show AVA's avatar, identity, approved personality/voice description, Persian-default language and presentation preferences. Keep AVA's identity separate from Amir's identity and other people's profiles. Person-profile management is a separate entry governed by access level.

### 5.5 Voice and language — صدا و زبان

Show only controls supported by the active engine: microphone permission, input/output availability, selected response language (Persian default), voice/rate only if actually exposed, real listening/processing/speaking/paused/error states and explicit stop.

Permission denial, audio route failure and recognition errors have distinct recovery messages. Stopped/replaced tasks ignore stale callbacks. Speaker-verified automatic interruption remains unavailable until noise/echo discrimination and latency requirements are implemented and verified. Never imply always-on listening.

### 5.6 AVA SECRET

List: search, category/language filter, term, meaning, status, version and last-change date. Statuses: پیش‌نویس، تأییدشده، نیازمند بازبینی، بایگانی‌شده.

Detail: primary term, meaning, category, base language, multilingual equivalents, optional pronunciation and version history. Owner-only actions: edit, approve, rollback as a new draft, archive.

New entries start as drafts. Editing an approved entry returns it to draft. Historical versions remain inspectable. Rollback creates a new draft; archive is not represented as permanent deletion. Non-owner sessions cannot change authoritative vocabulary.

### 5.7 Daily assistant — دستیار روزانه

Groups: today, upcoming, completed. Show task title and optional due date. Add, complete, defer and delete map to real stored operations. Delete confirms the exact task. Do not show reminder/notification controls until a real notification service and privacy behavior exist. Declined follow-ups stay dismissed.

### 5.8 Project control center — کنسول مرکزی

Show module, status and last update only when known. Status vocabulary: طراحی‌شده، در حال پیاده‌سازی، پیاده‌سازی‌شده، نیازمند بررسی، غیرفعال. Distinguish design approval, source implementation, test verification and device verification. Never invent CPU/RAM/network metrics or label source as tested/complete without evidence. Sensitive controls are owner-only.

### 5.9 Security — امنیت و دسترسی

Show current mode (owner/person/public-only), active session identity/level, available permissions in plain Persian, safe session-end/lock action and authorized security history. Re-authentication explains the exact action. Sensitive confirmation names the target and consequence. Restricted users can always find the safe session-end action. If audit recording fails, sensitive operations stop. Never expose another person's private memory in cards, search or notifications.

### 5.10 Appearance — ظاهر و شخصی‌سازی

Controls: dark/light theme; approved palette and accent intensity; avatar gallery selection/crop/preview/reset; avatar size/visibility; glass opacity; text size; reduced motion; optional high contrast.

Show a live preview before applying. Provide اعمال تغییرات and بازگشت به پیش‌فرض. Persist settings across restarts. Resetting appearance must not touch memories, profiles, conversations or security settings. Gallery cancellation leaves the current avatar unchanged.

## 6. Shared interaction and state patterns

Every screen must define:
- Normal, loading, empty, error, locked/restricted and permission-denied states as relevant.
- One clear primary action; secondary actions visually quieter.
- Destructive action with exact target, consequence and confirmation.
- Dialog/bottom sheet that remains scrollable on short screens.
- Snackbar/toast only for transient acknowledgement, never as the sole record of a critical security result.
- Real loading only while an operation is active; cancellation where safe.
- Error copy that says what failed, whether existing data was preserved and the next safe action.

## 7. Motion and accessibility

- Short, restrained transitions for navigation, expansion and dialogs.
- Glow only for active/selected states; no constant decorative animation.
- Listening/speaking animation only while the real state is active.
- Respect Android reduced-motion settings.
- Default all screen layouts to RTL.
- Render URLs, email, code, paths and Latin identifiers in isolated LTR spans.
- TalkBack labels/state descriptions for every interactive control.
- Status is not communicated by color alone.
- Readable contrast in both themes, dynamic font scaling, 48 dp targets.
- Return accessibility focus to the invoking control after a dialog closes.

## 8. Implementation architecture contract

The current app uses native Android Views and programmatic Kotlin layouts. Preserve that baseline for the first UI implementation; do not introduce Jetpack Compose or a new UI framework as an incidental redesign choice.

Refactor presentation into reusable layers instead of expanding the already large MainActivity:
- ui/theme: palette, typography, spacing, glass surfaces.
- ui/components: buttons, cards, chips, avatar, status, dialogs, empty/error states.
- ui/navigation: destinations, selected tab, back behavior, access-aware visibility.
- ui/screens/home, chat, memory, ava, voice, secret, daily, console, security, appearance.

MainActivity coordinates lifecycle/navigation and existing controllers. Screens call existing storage, conversation and security services through narrow interfaces. Visual refactoring must not rewrite access-control, memory or backup policy. Centralize design tokens; avoid one-off hard-coded colors and spacing.

## 9. Performance and resilience

Prioritize smooth scrolling and responsive input on Redmi Note 11. Avoid full-screen blur, heavy perpetual animation and unnecessary image decoding. Load avatar at display size with safe fallback. Keep long pages scrollable. Preserve typed text through ordinary navigation. Handle missing gallery permission, unavailable storage, microphone denial, provider failure and session expiry without crashing or exposing private data.

## 10. Existing baseline and remaining work

The design/ui-2.0-glass-profile branch and PR #2 already contain source-level work for:
- Dark gradient/glass shell and rounded cards.
- Four-item bottom navigation.
- Home hero and quick actions.
- Glass chat bubbles and composer.
- Memory/profile pages.
- Avatar gallery selection/reset.
- Palette, glass opacity, font size and avatar presentation controls.
- Source pages for AVA SECRET, daily assistant, voice, console and security.

This is a source baseline, not a verified release. Remaining UI 2.0 work is to unify reusable components; refine the AVA hub/navigation hierarchy; apply the same system across every module; complete loading/empty/error/locked/permission states; correct RTL, keyboard, accessibility and compact-screen behavior; reduce MainActivity responsibility; and ensure every visible control maps to a real supported action.

## 11. Implementation order after design sign-off

1. Freeze this specification and record owner-approved amendments.
2. Create reusable theme tokens and glass components.
3. Refactor shell, top bar and four-destination navigation.
4. Refine Home and AVA hub.
5. Refine Conversation/composer without changing the conversation engine.
6. Refine Memory and Security with strict role-aware visibility.
7. Refine AVA SECRET draft/version/approval lifecycle.
8. Refine Voice/language using real service state only.
9. Refine Daily assistant and Project control center.
10. Finish Appearance and persistence.
11. Run static source review and document integration gaps.

## 12. Acceptance checklist

- [ ] Approved lavender/pink/rose-gold identity is consistent.
- [ ] Dark theme is default; light theme remains readable.
- [ ] Four-item navigation is comfortable on Redmi Note 11.
- [ ] Every module is reachable through the AVA hub.
- [ ] Home/chat show only real service states.
- [ ] Composer remains visible with keyboard open.
- [ ] Avatar preview/reset is clear and persistent.
- [ ] Memory/Security respect owner/person/public-only boundaries.
- [ ] AVA SECRET accurately shows draft, approval, version, rollback and archive.
- [ ] Voice UI never implies always-on listening or unimplemented interruption.
- [ ] Daily task actions map to real stored operations.
- [ ] Console distinguishes design/source/test/device status.
- [ ] Appearance changes do not mutate user data or security.
- [ ] RTL, TalkBack, font scaling, contrast and touch targets are addressed.
- [ ] Relevant empty/loading/error/locked/permission states exist.
- [ ] No control advertises an unconnected action.
- [ ] UI work does not weaken backend/security/backup behavior.
- [ ] No Build, APK, CI, test execution or physical-device test occurs unless Amir explicitly changes the prior instruction.


## 13. Shared component inventory and visual states

The reusable presentation layer should standardize these components:

- AvaTopBar and AvaBottomNavigation
- AvaGlassCard and AvaSectionHeader
- AvaPrimaryButton, AvaSecondaryButton and AvaIconButton
- AvaStatusChip and AvaInlineNotice
- AvaAvatar and AvaProfileHeader
- AvaMessageBubble and AvaComposer
- AvaModuleTile
- AvaEmptyState, AvaLoadingState and AvaErrorState
- AvaConfirmDialog and AvaPermissionNotice

Each interactive component defines normal, pressed, focused, disabled and loading states where applicable. Destructive actions use a separate danger treatment and must identify the exact target. Glass opacity must never reduce readable text contrast.

## 14. Cross-screen state contract

The following states must be consistent across the app:

- **Loading:** shown only while a real operation is active; no indefinite decorative spinner.
- **Empty:** explains what is absent and offers one relevant next action.
- **Error:** explains what failed, whether existing data was preserved, and the next safe step.
- **Restricted:** identifies that the current session cannot access the section without previewing protected information.
- **Permission required:** names the Android permission and explains why it is needed.
- **Unavailable:** distinguishes unsupported device/service capability from a temporary failure.
- **Saved:** shown only after persistence reports success.
- **Unsaved changes:** visible before leaving or discarding edits.

A status indicator must always come from a real state source. Do not derive listening, speaking, backup health, storage readiness, project completion, or network connectivity from animation or assumptions.

## 15. Owner sign-off and implementation authorization

### Approved by Amir — 2026-11-17
- The four primary destinations and exact order are approved: **خانه، گفتگو، حافظه، آوا**.
- The fourth destination is the **آوا hub**, not a profile-only destination.
- The AVA hub is the single entry point for: هویت و شخصیت آوا، صدا و زبان، AVA SECRET، دستیار روزانه، کنسول مرکزی، امنیت و دسترسی، ظاهر و شخصی‌سازی.
- The approved navigation and hub structure is frozen as the implementation baseline.
- UI source implementation may proceed in a separate branch and pull request, following the implementation order in this specification.

### Implementation constraints
- Preserve the approved Sultan-lavender/pink, rose-gold and deep-purple identity. Do not introduce a blue-led palette.
- Numeric color values in section 3 are starting tokens, not a new brand-direction approval. The implementation must preserve readable contrast and may tune opacity within the approved palette.
- Preserve the native Android Views baseline. Do not introduce Jetpack Compose.
- The UI must call existing services and policies. This visual refactor must not weaken or rewrite access-control, memory, backup, conversation-transmission or audit behavior.
- Sensitive actions must continue using the existing authorization and confirmation flows.
- The current four-tab approval does not claim that any screen is implemented or verified.

## 16. Verification boundary

The design approval authorizes source implementation only. It does not mean the UI has been compiled, tested, packaged, or verified on a device.

The current owner instruction still prohibits Build, tests, CI, APK generation and physical-device verification. Source implementation and static source review may proceed while those verification actions remain paused unless Amir explicitly changes the instruction.
