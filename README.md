# MoneyLupe Design System

MoneyLupe is a **personal banking statement analyzer app** (Android & iOS via Kotlin Multiplatform / Compose). It helps users **import PDF or CSV bank statements**, **automatically categorize transactions**, and **track spending trends over time**. The app is privacy-first — it runs on-device, works offline, and supports 20+ European/international banks (Deutsche Bank, Commerzbank, DKB, ING, Bunq, Apo Bank, Consorsbank, C24, etc.).

The product was originally named **"Bank++"** and has been rebranded to **MoneyLupe** ("lupe" = magnifying glass, in German/French) — a nod to the magnifying-glass metaphor of looking closely at your finances. An owl ("sova") is the brand mascot — wise, watchful, gentle.

> Tagline surfaces: _"Analyze. Organize. Simplify."_ • _"Welcome to MoneyLupe!"_ • _"Import your bank statement"_

## Sources

- **Codebase:** `andybuki/BankingStatement` on GitHub (Kotlin Multiplatform + Jetpack Compose Material3). Primary files read:
  - `composeApp/src/commonMain/kotlin/com/banking/statement/ui/theme/Theme.kt` — color palette (`AppColors` object).
  - `composeApp/src/commonMain/kotlin/com/banking/statement/LocalizedStrings.kt` — voice, copy examples, category names.
  - `composeApp/src/commonMain/kotlin/com/banking/statement/ui/OnboardingScreen.kt`, `HomeScreen.kt`, `SpendingOverviewScreen.kt`, `BottomNavigation.kt`, `TransactionListScreen.kt`.
  - `composeApp/src/commonMain/composeResources/drawable/sova.xml` — owl mascot vector (converted to `assets/owl_mark.svg`).
  - `composeApp/src/commonMain/composeResources/drawable/logo.png` — legacy "Bank++" launcher icon (kept for reference only).
- **Privacy policy page:** `reference/privacy_policy.html` (was `docs/index.html`).

## Products represented

- **MoneyLupe mobile app** (Android/iOS) — the only surface. Five tabs: Home, Activity, Spending, Merchants, Settings.

## Index

- `README.md` — this file. Read top-to-bottom.
- `SKILL.md` — Agent-Skill stub; invoke to generate MoneyLupe-branded artifacts.
- `colors_and_type.css` — design tokens (CSS variables) — colors, type scale, spacing, radii, shadows.
- `assets/` — logo, owl mark, feature graphic, icon references.
- `fonts/` — _empty_. The app uses system fonts (Roboto on Android, SF Pro on iOS); see VISUAL FOUNDATIONS.
- `preview/` — one HTML card per design-system concept. Registered to the Design System tab.
- `ui_kits/mobile/` — hi-fi mobile recreation. Components + `index.html` click-through.
- `reference/` — misc. reference docs (privacy policy).

---

## CONTENT FUNDAMENTALS

**Voice:** Warm, plain-spoken, assistive. MoneyLupe talks _to_ the user, not _about_ itself. Copy is direct imperative or light-second-person. No exclamation points except at the very top of onboarding / celebration moments ("Welcome to MoneyLupe!", "Account Created!", "Import Complete!"). No corporate hedging, no jargon.

**Casing:** Sentence case everywhere — button labels, navigation, titles, dialog headers. "Import Statement", "Manage Accounts", "New Bank Statement" use Title Case only for multi-word buttons / section headers; body copy is sentence case.

**Person:** Mostly **second-person** ("your transactions", "where your money goes", "tap to view"). Occasional first-person plural when the app is doing something on the user's behalf ("we'll handle the rest", "we'd love to hear from you!").

**Tone markers:**
- **Reassuring:** "No manual entry needed." / "Just drop in a PDF or CSV file and we'll handle the rest."
- **Concrete, not abstract:** "Supports 20+ banks." / "Watch your income and expenses evolve month by month."
- **Honest about limits:** "Bank not supported? Email moneylupe.info@gmail.com to request it — we'll add it very soon!"
- **Short:** most body copy is one short sentence. Onboarding subtitle caps at ~90 chars.

**Emoji usage:** _Very restrained._ One known instance: `👆 Tap to view top transactions` on spending details. Do not sprinkle emoji. Never use emoji as a substitute for an icon.

**Numbers & currency:** Shown with locale-appropriate formatting; the app is designed around EUR primarily (German bank focus) but handles any. Red for expenses, green for income. Net balance is colored by sign.

**Example copy to lift from:**
- Onboarding: `"Import your bank statement"` / `"Supports 20+ banks. Just drop in a PDF or CSV file and we'll handle the rest."`
- Welcome: `"Welcome to MoneyLupe!"` with bullets `"Import PDF/CSV bank statements"`, `"Track spending by category"`, `"Analyze trends over time"` and a button `"Got it, let's start"`.
- Empty state: `"No transactions yet"` / `"Import a bank statement to see your transactions"`.
- Error empathy: `"What happened"` / `"What you can do"` — paired labels in error dialogs.
- Home subtitle: `"Import your bank statements to analyze"`.

**Vibe in one line:** _Patient, wise, on-your-side._ Think "a friend who is good with spreadsheets", not "a fintech startup".

---

## VISUAL FOUNDATIONS

**Palette DNA:** A **deep navy header** sitting on a **white canvas**, punctuated by a single **confident blue** accent. Income green and expense red are reserved exclusively for money values — never decoration.

- **Navy `#0F2A44`** — app header + status-bar region. This is the signature. It reads like a private banker's suit, not a tech brand.
- **Blue `#2563EB`** — the only accent. Used for primary buttons, active nav, links, active chart bars. Has a lighter sibling `#3B82F6` and a darker `#1D4ED8` for press states.
- **White `#FFFFFF`** — card + footer background, and all content surfaces below the header.
- **Green `#16A34A`** / **Red `#DC2626`** — semantic money colors. Income / Expenses / Net ±.
- **Slate greys** — `#0F172A` (primary text), `#475569` (secondary text), `#94A3B8` (tertiary / inactive icons + labels like "Income"/"Expenses" under the header), `#E5E7EB` (dividers / borders), `#F1F5F9` (tint surfaces), `#CBD5E1` (disabled).
- **Tertiary purple `#7C3AED`** — barely used; available in `tertiary` slot of the M3 scheme for rare accents (e.g. category chips, subscription pills). Don't lean on it.
- **Owl pupils / stroke `#3D3B3C`** — near-black used only in the mascot.

**Type:** The codebase ships **no custom font** — it inherits Material 3 defaults. On Android that resolves to **Roboto**; on iOS to **San Francisco (SF Pro)**. For web recreations we preload **Roboto** (400/500/700) and **Roboto Mono** (amounts + IBANs) so a browser preview matches the Android build, with a system-font fallback chain so Apple devices render in SF Pro natively.
> ℹ️ **No substitution to flag.** The repo has no font files and no `FontFamily(Font(...))` declarations. If the team ever adopts a branded typeface, drop the `.ttf`/`.woff2` files into `fonts/` and update `--ml-font-sans`.

**Type scale** (see `colors_and_type.css`):
- Display / H1: 32px / 700 / -0.01em — screen titles like "Spending Overview".
- H2: 24px / 600 — card titles ("Spending by Category").
- H3: 18px / 600 — list section headers.
- Body: 16px / 400 — default.
- Small: 14px / 500 — labels, nav captions, metadata.
- Caption: 12px / 500 / uppercase-ish — "INCOME", "EXPENSES" secondary headers.
- Amount-large: 28–34px / 600 / Roboto Mono — hero balance.
- Amount: 16px / 500 / Roboto Mono — row amounts.

**Spacing:** 4-based scale. 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Card inner padding = 16; screen horizontal padding = 16; section gap = 24.

**Corner radii:** `8px` (chips, small buttons), `12px` (inputs, medium buttons), `16px` (cards — the default), `24px` (hero cards, bottom sheets), `999px` (pill badges, the FAB).

**Backgrounds:**
- Main app background = **`#0F2A44` navy** (the header extends into scroll pull-down, feels like one surface).
- Content cards sit on navy as white sheets → creates a "floating stack of receipts" feel.
- No gradients. No grain. No full-bleed photography. No patterns.
- Illustrations appear only on onboarding slides — simple flat, duotone, matching the palette. If you don't have one, leave a sized placeholder rectangle.

**Borders:** Hair lines only — `1px solid #E5E7EB`. Cards may have no border + shadow OR border + no shadow, never both.

**Shadows:** Soft, short, neutral. Three elevations:
- `xs`: `0 1px 2px rgba(15,23,42,0.04)` — resting card on white.
- `sm`: `0 2px 8px rgba(15,23,42,0.06)` — card on navy (floats above header).
- `md`: `0 8px 24px rgba(15,23,42,0.10)` — bottom sheets, dialogs, popover.
  No inner shadows. No colored shadows.

**Animation:** Material-standard: 200ms for state changes, 300ms for screen transitions, **standard ease-in-out** (`cubic-bezier(0.4, 0.0, 0.2, 1)`). Fades + small 8px vertical slides. No bounces. No parallax. Chart bars animate-in with a 400ms `ease-out` sweep left-to-right.

**Hover / press states:**
- Buttons on **press**: 94% scale (`scale(0.98)`) + primary-dark fill.
- Rows on **press**: `#F1F5F9` surfaceVariant wash, no scale.
- Icons on **press**: opacity 0.6, 150ms.
- No hover states for mobile; web/preview uses a subtle `#F8FAFC` row-hover tint.

**Transparency & blur:** Used sparingly. The app header has `backdrop-filter: blur(12px)` only when content scrolls under it. Bottom sheets use an 80% black scrim. Never use translucent navy over content.

**Imagery vibe:** Cool, crisp, minimal. The one brand image is the **owl mascot** — flat, geometric, multi-colored eye spokes (orange, magenta, purple, teal, gray, blue) on a navy-gray body. See `assets/owl_mark.svg`.

**Layout rules:**
- **Fixed header** (navy) with status-bar-safe padding + a bold white app title and small meta row ("Income · Expenses" pills).
- **Fixed bottom navigation** (white, 5 tabs, active = blue with label).
- Content scrolls between them. Use 16px horizontal screen padding.
- One FAB: the Import button — blue, bottom-right, shadow md.
- Cards: 16px radius, 16px padding, white fill, xs shadow, 12–16px vertical gap between them.

**Iconography philosophy:** See ICONOGRAPHY below. Lucide (outline, 1.75px stroke) is our CDN substitute for Material-Symbols-Outlined used in Compose.

**Charts:** Simple bars + donut. Categorical color scale drawn from category seed colors (see category list below). Axes use tertiary text. Gridlines `#E5E7EB`, 1px.

**Category seed colors** (for chips, pie slices, avatar backgrounds):
Rent `#0EA5E9` · Transport `#F59E0B` · Supermarket `#22C55E` · Restaurant `#EF4444` · Shopping `#EC4899` · Health `#14B8A6` · Insurance `#6366F1` · Entertainment `#A855F7` · Subscriptions `#8B5CF6` · Investment `#10B981` · Travel `#06B6D4` · Salary `#16A34A` · Refund `#84CC16` · Transfer `#64748B` · Education `#F97316` · Taxes `#71717A` · Other `#94A3B8`.

---

## ICONOGRAPHY

The Compose codebase uses **Material Symbols (Outlined)** via `androidx.compose.material.icons`. For our HTML recreations we substitute **[Lucide](https://lucide.dev/)** (same 1.75px outline aesthetic, MIT-licensed, CDN-available).

> ⚠️ **Substitution:** we do not ship Material Symbols fonts; Lucide is close enough in weight and joinery. Flag and swap if an official icon kit is adopted.

**Usage rules:**
- Stroke weight: 1.75 (Lucide default). Never mix with filled glyphs.
- Sizes: 20 / 24 / 28. Tab-bar icons = 24. Row-leading icons = 20. Header icons = 24.
- Color: inherits from text color. Tab-bar active = `#2563EB`, inactive = `#94A3B8`.
- **No emoji as icon.** The single emoji we observed in copy (`👆` on "Tap to view top transactions") is a hint glyph, not structural UI.
- **No unicode glyphs** (e.g. `✓`, `✕`) as icons — always a Lucide SVG.
- **Tab icons:** Home → `home`, Activity → `list` (transactions), Spending → `pie-chart`, Merchants → `store`, Settings → `settings`.
- **Action icons:** Import → `upload`, Export → `download`, Share → `share-2`, Delete → `trash-2`, Edit → `pencil`, Back → `chevron-left`, Add → `plus`, Search → `search`, Filter → `sliders-horizontal`, Calendar → `calendar`, Lock → `lock`.
- **Category icons (17):** rent→`home`, transport→`bus`, supermarket→`shopping-cart`, restaurant→`utensils`, shopping→`shopping-bag`, health→`heart-pulse`, insurance→`shield`, entertainment→`clapperboard`, subscriptions→`repeat`, investment→`trending-up`, travel→`plane`, salary→`wallet`, refund→`undo-2`, transfer→`arrow-left-right`, education→`graduation-cap`, taxes→`landmark`, other→`circle-dashed`.

**Brand logo:** `assets/owl_mark.svg` (converted from `sova.xml`). Use at 24–120px. On navy, the body reads almost as negative space — preserve the multicolor eye detail, do not recolor the mascot.

**Legacy asset:** `assets/logo.png` is the old Bank++ launcher icon — kept for traceability, **do not use in new designs**.
