# AVA INFINITY — Wardrobe Register
**Date:** 2026-11-20  
**Decision authority:** Amir Darbandi Pour  
**Status:** Approved design catalog consolidated; visual assets and final runtime verification remain pending.

## Approved wardrobe inventory
- **123 numbered approved designs** are listed in `app/src/main/assets/ava-wardrobe-catalog.json`.
- **4 approved core looks** are listed separately in the catalog: Royal Lilac Sport, Soft Casual, Royal Lilac Evening, and Cozy Lilac Home.
- The catalog includes the explicitly approved private, sporty, everyday, formal/evening, and homewear designs.
- Numeric gaps are intentional: designs that were only proposed or did not receive a clear approval are not silently marked approved.
- Every catalog entry is marked `APPROVED_BY_AMIR`. This records design approval, not the existence of a rendered image or a completed build.

## Privacy and presentation requirements
- These are designs for a fictional adult character.
- Keep the private wardrobe separate from sporty, everyday, formal/evening, and homewear collections.
- Public-facing navigation and previews must use a discreet label such as **مجموعه خصوصی**.
- Private entries must not be exposed in public-only sessions or to non-owner profiles.
- Require owner authorization / re-authentication before opening private wardrobe content.
- Keep garment imagery static. Do not animate the avatar or body.
- Preserve the approved Sultan-lavender, soft-pink, and rose-gold visual identity.

## Implementation status
- **APPROVED — DESIGN:** all entries in the catalog.
- **CATALOG:** consolidated in `app/src/main/assets/ava-wardrobe-catalog.json`.
- **APP UI:** the wardrobe screen reads the catalog from app assets and displays entries through the existing wardrobe page.
- **IMAGE ASSETS:** pending. Web sample images shown during design review are references, not repository assets. No image files have been added by this catalog update.
- **BUILD VERIFIED / DEVICE VERIFIED:** not claimed. No GitHub Actions or APK build was started for this catalog update.

## Change-control
Implement only on `ava/mobile-app-integration`. Keep `master` untouched and PR #1 in Draft. Do not start the final build until image assets, full regression review, and privacy-gate verification are complete.


## Hairstyle catalog
- Added `app/src/main/assets/ava-hairstyle-catalog.json` with six hairstyle references.
- Amir's selected base style: **Messy High Bun / دم‌اسبی گوجه‌ای**.
- Ava's style suggestions: Glossy High Ponytail, Half-Up Soft Waves, Low Messy Bun, Side Braid & Waves, and Claw-Clip French Twist.
- The owner wardrobe page now reads and lists the hairstyle catalog.
- The image URLs are external inspiration references only. They are not local image assets and are not embedded in the APK.


## Visual reference progress (2026-11-20)

- Added curated external image references for the four approved core looks: Royal Lilac Sport, Soft Casual, Royal Lilac Evening, and Cozy Lilac Home.
- The six hairstyle entries retain their selected external image references.
- Both catalogs explicitly mark these as `EXTERNAL_REFERENCE_ONLY`. They are reference URLs, not downloaded/bundled image files.
- The 123 numbered wardrobe designs remain approved catalog entries with local visual assets pending. Do not count external links as local image assets.
- No Android build or GitHub Actions workflow was run as part of this visual-reference update.
