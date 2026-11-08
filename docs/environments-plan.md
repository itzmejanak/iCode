# iCode's environments

Status: **built** (2026-11-08), the owner's request: E0 (016301c), E1 (dcbf084) and E2, every decision in
§6 as recommended. §7 says where the build went past the plan. The phone checks (§4) are still to be run.

> "The goal is simple: identify the iCode app by its current type." — development, staging, and
> production as preview, beta, stable and latest. "Make sure the color theme and accent match the
> icons; just use the existing icons per environment; no new labels, no clutter."

**The answer:** each environment is its own installable app with the icon already drawn for it
(`design/icons`, the owner's commit b9e2a79) and a theme accent taken from that icon. Nothing else
changes on screen: no banners, chips or extra rows.

## 1. The environments

| Environment | Who uses it | Package (side by side) | Icon (existing) | Accent: Dark, AMOLED / Light | Built | Updates from |
|---|---|---|---|---|---|---|
| Development | the owner, from Android Studio | `dev.icode.mobile.debug` (as today) | `development/` grey and slate | `#B0A99A` / `#757167` | `debug`, locally | none |
| Staging | the owner, before a release | `dev.icode.mobile.staging` | `staging/` lavender and violet | `#9C85C9` / `#6E5AA0` | release settings, CI on demand | none |
| Preview | early testers | `dev.icode.mobile.preview` | `production/preview/` | Ember, as today | release, tag `v0.2.0-preview.1` | the website's `preview` channel |
| Beta | testers | `dev.icode.mobile.beta` | `production/beta/` | Ember | release, tag `v0.2.0-beta.1` | `beta` |
| Stable | everyone | `dev.icode.mobile` (as today) | `production/stable/` (the original) | Ember | release, tag `v0.2.0` | `stable` |

- **Latest** is not a separate build. It is the newest stable release: what the website's stable
  channel answers and what GitHub marks "Latest release". It has the stable icon.
- **Accents** are the icon's own colours, in the role Ember plays in the stable icon (its warm tone).
  On Light, the colour is deepened to read on a light background, as Ember already is: `#757167` and
  `#6E5AA0` (the staging icon's violet) give 4.5:1 and 5.4:1. The Dark and AMOLED values give 8.4:1
  and 6.2:1 on the dark background. The rest of each theme (its neutral surfaces) stays as it is.
- An accent the user chose in Appearance still wins, as today. The environment's colour is the theme's
  own accent, the one the accent sheet offers first.

## 2. Facts this plan rests on (checked 2026-11-08)

- **iCode today:**
  - `debug` already installs beside the release as `dev.icode.mobile.debug`.
  - Provider authorities and the sign-in scheme use `${applicationId}`, so side-by-side installs do
    not clash.
  - `release.yml` sets the channel to `beta` for any version with a `-`, else `stable`.
  - The update check always asks the website for `stable` (`ICodeWebClient.latestRelease`).
  - `versionCode` is fixed at 1.
- **Firefox for Android** (mozilla-mobile/fenix, `app/build.gradle`):
  - One build type per channel: `debug`, `nightly`, `beta` and `release`.
  - Each has its own package (`org.mozilla.fenix.debug`, `org.mozilla.fenix`,
    `org.mozilla.firefox_beta`, `org.mozilla.firefox`) and its own launcher icons in
    `app/src/<channel>/res`.
  - This plan does the same.
- **Signal for Android** (`app/build.gradle.kts`) has a `staging` flavor with `applicationIdSuffix
  ".staging"`, pointed at its staging servers. **Mihon** gives its `debug` build its own launcher icon
  in `app/src/debug/res`.
- **Android's adaptive icons** (AOSP `AdaptiveIconDrawable`):
  - The layers are 108 × 108 dp; the inner 72 dp shows, and the inner 66 dp survives every mask.
  - Today's mark is 43 dp tall in that layer, inside the 66 dp. The existing artwork goes in the same
    place.
- **The labels at home-screen size:**
  - PREVIEW and BETA letters are 39 px of the 873 px mark, so about 1.3 to 1.6 dp tall on a 48 to
    60 dp launcher icon.
  - They read in Settings, the app list and the website; on a home screen, preview and beta look like
    stable at a glance.
- **Themed icons** (Android 13 and later) draw one monochrome shape in the user's colours, so with
  them turned on every environment looks alike. The monochrome shape stays as it is.
- **Play's tracks**, if iCode goes there later (gradle-play-publisher): `internal`, `alpha`, `beta`,
  `production`. Preview fits `alpha`, beta fits `beta` and stable fits `production`.

## 3. What changes

- **Build types**, Firefox's way: `debug` (development) and `release` (stable) keep their names, so CI
  and its tasks stay. `staging`, `preview` and `beta` are added, each starting from `release`
  (`initWith`), with:
  - its package suffix and version suffix
  - its update channel and website address (`BuildConfig`)
  - its signing
- **Icons:** each build type's `src/<type>/res` gets its environment's existing PNG as the adaptive
  icon's foreground, at the five densities, placed as today. Background (black) and monochrome stay
  shared in `main`.
  - The same `src/<type>/res` also carries the in-app mark, `drawable-*/icode_launcher_art`, which
    `IcodeBrandMark` draws on the splash, About, Assistant settings, the welcome card, the empty editor
    and the phone bubble: without it those screens showed the stable icon in every build. It is the
    environment's mark at `main`'s own framing (x 0.2097–0.7879, y 0.1236–0.8413 of the tile, on black),
    so the mark keeps its size and place.
  - `release` keeps `main`'s original icon.
  - The three new build types reuse `release`'s no-op capture code, as `debug` alone carries the
    screenshot tools.
- **Accent:** `AccentColorRules.themeAccent` returns the environment's colour (§1). It is one function
  already used by every theme and by the accent sheet.
- **Versions:**
  - `versionName` stays `0.2.0` in Gradle, and the tag adds `-preview.N` or `-beta.N`.
  - `versionCode` = MAJOR·10 000 000 + MINOR·100 000 + PATCH·1 000 + stage·100 + N, with preview
    stage 1, beta 2 and stable 9. It rises within every channel and across them. Order is by
    `versionCode`, never by name: in SemVer, `beta` sorts before `preview`.
- **Release workflow:** the tag's suffix picks the build type (`preview`, `beta` or none) and the
  website channel. Preview and beta are GitHub pre-releases, and stable is marked Latest. Staging gets
  a manual workflow that builds and signs it as an artifact, published nowhere.
- **Update check:** each build asks the website for its own channel. A beta install can only update to
  a beta APK, since stable is another package.
- **The website (icode-web, outside this repository):** it needs to accept the `preview` channel, and
  needs a staging address for the staging build.

Each package keeps its own data: workspaces, chats, keys, settings and its own runtime download
(Ubuntu base, then any tools). Moving between environments starts fresh, as with Firefox Beta and
Firefox.

## 4. Cycles

| # | What | Checked by |
|---|---|---|
| E0 | The three build types; the icons into each `src/<type>/res`; the accent per environment | `assembleStaging`, `assemblePreview` and `assembleBeta` build; a unit test of `themeAccent` and its contrast for every environment and theme; the guards |
| E1 | `versionCode` from the version; the release workflow by tag suffix; the staging workflow; each build's update channel | a dry run of the workflow's version step on each tag form; the update check's channel per build |
| E2 | Docs as built: this plan, the README's install note, the changelog | — |

**Phone checks:** install development, staging, beta and stable side by side. Each shows its own icon
in the launcher and Settings › Apps, and its own accent in Dark and Light. A stable installed over
an older stable keeps its data.

## 5. Not now

- Play Store tracks (§2 gives the mapping).
- A different launcher name per environment ("iCode Beta"): the owner asked for no new labels (§6 D2).

## 6. Owner decisions (taken 2026-11-08: all as recommended)

| # | Decision | Recommended |
|---|---|---|
| D1 | Every environment is its own package, so all five can be installed at once (Firefox's way). The other way: preview and beta replace stable in place (Play's testing way) | Separate packages |
| D2 | The launcher name stays "iCode" for every environment; the icon tells them apart (§2: preview and beta look like stable on a home screen) | "iCode" for all, as asked |
| D3 | The theme accent comes from the icon (§1); a user's own accent still wins | Yes |
| D4 | "Latest" is the newest stable release, not a separate build | Yes |
| D5 | Staging uses the website's staging address; development uses none unless set locally | Yes |

## 7. As built

- **Icons:** each environment's artwork is placed where the stable mark sits in today's launcher
  foreground, at every density: the same height (172 px at xxxhdpi, 43 dp of the 108 dp layer) and the
  same centre, each within a pixel. Background and monochrome stay shared.
- **The accent:** `AppEnvironment` (domain) holds each environment's two colours, and
  `AccentColorRules.themeAccent(theme, environment)` gives them; `BuildConfig.ENVIRONMENT` names this
  build's. A test holds every environment's accent to 3:1 on every theme's background, and what is
  drawn on it to 4.5:1.
- **versionCode** is worked out in `defaultConfig` from `versionName` and the channel and number the
  release workflow passes (`-PreleaseChannel`, `-PreleaseNumber`). A build type cannot carry a version
  code of its own, and this way `BuildConfig.VERSION_CODE`, which the update check sends, is the code
  built. 0.1.0 stable is 100900, above v0.1.0's 1.
- **The release workflow** reads the version's name, code and minimum SDK from the built APK. Staging
  is the same workflow run by hand, not a second file: it builds against `ICODE_WEB_STAGING_URL`.
- **CI** runs the unit tests it ran before (debug and release): the three new types are release with
  another icon, accent and package. They are first built by the release workflow, since this session
  cannot reach Google's Maven to build an APK.
- **Update checks:** `BuildConfig.UPDATE_CHANNEL` is each build's channel, empty for development and
  staging, which then say updates are not available in this build, as an unconfigured build already did.
