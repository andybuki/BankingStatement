---
name: MoneyLupe Design System
description: Use this skill when creating designs, mockups, marketing assets, or any visual artifact for MoneyLupe — the bank-statement analyzer mobile app (Kotlin Multiplatform, Android/iOS). Also use when recreating MoneyLupe product screens for decks, landing pages, or prototypes.
---

# MoneyLupe Design System

MoneyLupe is a privacy-first bank-statement analyzer for Android + iOS. Owl mascot (Sova). Deep-navy header on white canvas, a single confident blue accent, income-green / expense-red reserved exclusively for money values. Read `README.md` first — it has the full brief (content, visual, iconography).

## Before you build

1. **Read `README.md` top-to-bottom.** It is the source of truth for voice, color rationale, type, spacing, iconography.
2. **Link `colors_and_type.css`** — every design token lives there as CSS variables (`--ml-navy`, `--ml-blue`, `--ml-income`, `--ml-expense`, category seeds, spacing, radii, shadows). Use these tokens; don't invent colors.
3. **Check the `preview/` cards** for what each token looks like in practice (colors, type, cards, buttons, nav, mascot, voice).
4. **Use `ui_kits/mobile/`** as a component library when recreating screens:
   - `Icons.jsx` — `MLIcon` (Lucide-style 1.75px) + `ML_CATEGORIES` (color + icon per of 17 categories).
   - `Primitives.jsx` — `MLPhone`, `MLStatusBar`, `MLHeader`, `MLTabBar`, `MLScroll`, `MLCard`, `MLSectionTitle`, `MLCatAvatar`, `MLAmount`, `MLPeriodPills`.
   - `ScreensA.jsx` — `MLHomeScreen`, `MLActivityScreen`, plus `MLTxRow`, `MLCatBar`, `MLQuickCard`, `ML_SAMPLE_TX`.
   - `ScreensB.jsx` — `MLSpendingScreen`, `MLMerchantsScreen`, `MLSettingsScreen`, plus `MLDonut`, `MLBarChart`, `MLSetRow`, `MLSwitch`, `MLChip`.

   Load in this order (babel scripts):
   ```html
   <link rel="stylesheet" href="../../colors_and_type.css">
   <script type="text/babel" src="./Icons.jsx"></script>
   <script type="text/babel" src="./Primitives.jsx"></script>
   <script type="text/babel" src="./ScreensA.jsx"></script>
   <script type="text/babel" src="./ScreensB.jsx"></script>
   ```

## Non-negotiables

- **Typography:** **Roboto** (UI) + **Roboto Mono** (amounts / IBANs). These are system-default proxies for Android Roboto / iOS SF Pro. Do not introduce new fonts without asking — MoneyLupe has no branded typeface.
- **Color discipline:**
  - Navy `#0F2A44` = header only. Never as a button.
  - Blue `#2563EB` = **the only** accent. One primary CTA per screen.
  - Green `#16A34A` = income. Red `#DC2626` = expenses. **Never** decorative.
  - Tertiary purple `#7C3AED` is available but should stay rare.
- **Radii:** 16px on cards (default). 12px on buttons/inputs. 24px on hero/sheets. 999px on pills/FAB.
- **Shadows:** neutral only — `rgba(15, 23, 42, 0.04–0.14)`. Never colored.
- **No gradients, no grain, no photography, no emoji-as-icon.**
- **Iconography:** Lucide outline, 1.75px stroke, 20/24px. Use the pre-built `MLIcon.*` set first; fall back to Lucide only if missing.
- **Mascot (`assets/owl_mark.svg`):** never recolor. Use at 24–140px. Keep the multicolor eye detail visible.

## Voice checklist for any new copy

- Sentence case everywhere except multi-word button labels.
- Second-person (`your transactions`), occasional `we'll` when the app does work for the user.
- One short sentence per surface.
- Empty state: state + one actionable next step (`"No transactions yet" / "Import a bank statement to see your transactions"`).
- Error dialog: pair **What happened** with **What you can do** — explain, then empower.
- Celebration moments (`"Welcome to MoneyLupe!"`, `"Account Created!"`, `"Import Complete!"`) are the **only** place for exclamation points.
- No emoji, except the one exception already in the codebase (`👆` on "Tap to view top transactions").

## Category color/icon cheatsheet

When any design references categories, pull from `ML_CATEGORIES` — it already pairs the right seed color with the right Lucide glyph for all 17:
Rent → home · Transport → bus · Supermarket → cart · Restaurant → utensils · Shopping → bag · Health → heart · Insurance → shield · Entertainment → clap · Subscriptions → repeat · Investment → trending · Travel → plane · Salary → wallet · Refund → undo · Transfer → arrowLR · Education → cap · Taxes → landmark · Other → dashed.

## Common tasks

- **Recreating a MoneyLupe app screen:** import `MLPhone` and drop one of the five screen components inside. See `ui_kits/mobile/index.html` for the pattern.
- **Marketing landing page:** link `colors_and_type.css`, use `.ml-h1/.ml-p/.ml-btn` utilities. Lead with navy hero + owl mark.
- **Play Store screenshots:** render the mobile kit screens at 1080×1920 against `#0F2A44` with generous white padding; overlay copy in Roboto 700.
- **New feature mockup:** start from the closest existing screen in `ui_kits/mobile/ScreensA.jsx` or `ScreensB.jsx`, copy the component, and modify. Don't hand-roll new chrome.

## Things to flag or ask about

- The Android launcher icon on-device still reads "Bank++" (legacy pre-rebrand). If a task involves the launcher icon, surface this.
- The Compose codebase has no typography file — if a design calls for a branded face, ask first.
- If a design needs a category beyond the 17 predefined, ask before inventing — the app supports custom categories but they are user-defined at runtime.
