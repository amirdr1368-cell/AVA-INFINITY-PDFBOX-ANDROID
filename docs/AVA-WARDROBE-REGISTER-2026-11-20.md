# AVA INFINITY — Wardrobe Register
**Date:** 2026-11-20  
**Decision authority:** Amir Darbandi Pour  
**Status:** Design approvals recorded; app integration in progress

## Latest approved additions
All six entries below were explicitly approved by Amir for Ava's protected private wardrobe.

| ID | Name | Design |
|---:|---|---|
| 136 | Lilac Moon | Long lavender satin nightdress with delicate trim |
| 137 | Burgundy Lace | Burgundy bodysuit with lace detailing |
| 138 | Champagne Robe | Champagne satin kimono robe |
| 139 | Plum Whisper | Plum satin lounge set |
| 140 | Royal Lace | Lavender set with delicate lace |
| 141 | Rose Infinity | Rose-gold satin nightdress |

## Privacy and presentation requirements
- These are designs for a fictional adult character.
- Keep the private wardrobe separate from sporty, everyday, formal/evening, and homewear collections.
- Public-facing navigation and previews must use a discreet label such as **مجموعه خصوصی**.
- Private entries must not be exposed in public-only sessions or to non-owner profiles.
- Require owner authorization / re-authentication before opening private wardrobe content.
- Keep garment imagery static. Do not animate the avatar or body.
- Preserve the approved Sultan-lavender, soft-pink, and rose-gold visual identity.

## Implementation status
- **APPROVED — DESIGN:** all six entries.
- **DOCUMENTED:** this register and `app/src/main/assets/ava-wardrobe-catalog.json`.
- **IMAGE ASSETS:** pending. The sample images shown during design review are visual references, not repository assets.
- **APP UI:** an owner-only catalog screen is implemented in `MainActivity.kt` and linked discreetly from the AVA hub.
- **RUNTIME ACCESS CONTROL:** the screen requires an active, approved Level 1 owner session (`owner`) and refuses public-only, missing-session, and non-owner contexts. The route is also behind the existing CONTROL access gate.
- **SOURCE REVIEW:** route, hub entry, owner gate, and asset-catalog read path were confirmed in the current branch source.
- **BUILD VERIFIED / DEVICE VERIFIED:** not claimed. No GitHub Actions or APK build was started for this change.

## Change-control
Implement only on `ava/mobile-app-integration`. Keep `master` untouched and PR #1 in Draft. Do not start the final build until the wardrobe UI, privacy gate, image assets, and regression review are complete.
