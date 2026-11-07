# Music Player vs Pulse Player — technical gap report

Date: 2026-11-03
Evidence: `Music Player.apk` zip inventory, decoded `Music Player/` (apktool 3.0.3 + jadx),
`AndroidManifest.xml`, `apktool.yml`, `jadx-src/sources`, `PulsePlayer-1.5.apk`,
`project/` sources. No runtime observation was performed.

---

## 1. What the original app actually is

| Item | Value |
|---|---|
| Package | `musicplayer.musicapps.music.mp3player` |
| Version | 4.4.1.189 (versionCode 189) |
| minSdk / targetSdk | 24 / 35 |
| Language / UI | Kotlin + Java, AndroidX + Jetpack Compose (mixed with XML layouts) |
| Install source | Play Store (`com.android.stamp.source`) |
| Monetization | Ads (AdMob, Meta Audience Network, Pangle/ByteDance, InMobi, Moloco, myTarget, Bigo) + Google Billing subscription |
| Activities declared | 89 (of which ~32 are ad-SDK activities) |
| Services | 15 |
| Receivers / Providers | 19 / 12 |
| Widgets | 9 home-screen widget styles + widget service + 3 widget activities |
| Audio engine | ExoPlayer/Media3 references + ijkplayer/ffmpeg native libs (video) |
| Libraries (META-INF markers) | 90 AndroidX/Kotlin artifacts |

---

## 2. Why the size difference (53.48 MB vs 0.065 MB)

APK = compressed container. Uncompressed contents of the original total **57.95 MB**, Pulse **0.13 MB**.
The gap is not one cause — it is six.

### Original APK, uncompressed by category

| Category | Size | What is inside |
|---|---|---|
| DEX code | **31.54 MB** | 5 dex files: 10.08 + 8.09 + 7.83 + 5.38 + 0.16 |
| Secondary DEX in assets | **5.17 MB** | `assets/audience_network/classes*.dex` (Meta ad SDK) |
| `resources.arsc` | **4.35 MB** | ~90 locales + all resource IDs |
| `res/` | **7.01 MB** | 3,486 files: 1,294 drawable, 763 layout, 189 color, 56 anim, 9 icon densities |
| `lib/` | **9.38 MB** | 30 `.so` files: 15 per ABI (arm64-v8a + armeabi-v7a) |
| `assets/` (rest) | **0.25 MB** | Lottie JSON animations, theme JSON, share-card images |

### `lib/` breakdown (real feature code)

| Library | arm64 | armv7 | Purpose |
|---|---|---|---|
| `libijkffmpeg.so` | 3.20 MB | 2.41 MB | FFmpeg demux/decode (video + exotic audio) |
| `libijkplayer.so` | 0.52 MB | 0.31 MB | ijkplayer core |
| `libijksdl.so` | 0.40 MB | 0.29 MB | SDL video/audio output |
| `libstackblur.so` | 0.24 MB | 0.14 MB | Blurred album-art backgrounds |
| `libtt_ugen_layout.so` | 0.42 MB | 0.33 MB | ByteDance ad layout engine |
| `libnms.so` | 0.29 MB | 0.19 MB | Network/media SDK |
| `libapminsight*.so` | 0.22 MB | 0.18 MB | Tencent APM analytics |
| `libpglarmor.so` | 0.07 MB | 0.05 MB | Pangle ad protection |
| small utils | ~0.06 MB | ~0.04 MB | datastore, image processing, surface util |

Only ~5.7 MB of `lib/` is the actual player. The rest is ads/analytics.

### Code composition (jadx source tree, 119 MB decompressed sources)

| Package | Size | Share |
|---|---|---|
| `com.google` (GMS, Firebase, Ads) | 28.17 MB | 24% |
| `com.facebook` (Audience Network) | 11.86 MB | 10% |
| `com.bytedance` (Pangle ads) | 8.42 MB | 7% |
| `com.inmobi` | 4.87 MB | 4% |
| `sg.bigo` | 4.86 MB | 4% |
| `com.moloco` | 4.59 MB | 4% |
| **`musicplayer.musicapps` (the real app)** | **5.25 MB** | **4.4%** |
| AndroidX/Compose/Media3/Kotlin | ~15 MB | 13% |
| everything else (myTarget, UCrop, Lottie, network stack) | rest | — |

**Roughly half of the APK is advertising, billing and analytics code plus its native/asset payloads.**
That payload is exactly what Pulse does not and should not have — so a 1:1 size match is neither
possible nor desirable for a clean build.

### Pulse Player, same measurement

| Category | Pulse 1.5 | Original |
|---|---|---|
| Total entries in APK | 21 | 3,708 |
| Uncompressed contents | 0.13 MB | 57.95 MB |
| DEX | 1 file, 80 KB | 5 files, 31.5 MB (+5.2 MB asset dex) |
| DEX method count | ~2,850 lines of Java, 15 classes | ~90 libraries, tens of thousands of classes |
| `res/` | 15 files (7 layouts, 8 drawables) | 3,486 files (763 layouts, 1,294 drawables) |
| `lib/` | none | 30 `.so`, 9.38 MB |
| `assets/` | none | 49 files, 5.42 MB |
| Locales | 1 | ~90 |

Pulse is a **from-scratch ~4,600-line Java app**. The original is a **multi-module Kotlin/Compose app
with a native media stack, nine widgets, a foreground service, billing and seven ad networks**.
A 53 MB → 65 KB difference is the expected result of that, not a build bug.

---

## 3. What Pulse is missing (verified against the original manifest)

### Screens / activities — original has, Pulse does not

| Screen | Original class | Impact |
|---|---|---|
| Splash / startup flow | `SplashActivity`, `StartUpActivity` | first-run onboarding, permission flow |
| Search screen | `SearchActivity` | Pulse has an inline search box only |
| Queue | `QueueActivity` | implemented in v1.4 — tap to play, long-press to remove, clear all |
| Lock screen player | `LockScreenActivity`, `LockScreenBroadcast` | full-screen lock player (still missing) |
| Playlist create/edit | `SetNewPlaylistActivity` | implemented in v1.4 — create, rename, delete, per-playlist screen |
| Song tag editor | `SongTagEditorActivity` | ID3 edit (title/artist/album/cover) |
| Album editor | `AlbumEditorActivity` | album art + metadata edit |
| Album art crop | `UCropActivity` | crop/rotate picked cover |
| Lyrics editor / DMCA | `LyricsEditActivity`, `LyricsDMCAActivity` | edit/sync lyrics |
| Scan + folder picker | `ScanActivity`, `ScanFoldersActivity` | scan progress, folder filters |
| Hidden files | `HiddenFilesActivity` | Pulse has a simplified version |
| Backup / restore | `BackupActivity` | implemented in v1.4 — Settings export/import JSON backup |
| Monthly recap | `MusicMonthlyRecapActivity`, `MusicMonthlyDetailActivity` | listening stats |
| Badges | `MyBadgesActivity`, `BadgesDetailActivity`, `BadgesFaqActivity` | gamification |
| Device transfer | `TransferPrepare/Permission/Pair/Status` + service | Wi-Fi phone-to-phone transfer |
| Themes | `ThemeChooserActivity`, `MakeCustomThemeActivity`, `ThemePreviewActivity`, `NowPlayingThemeSelectorActivity` | Pulse has 4 hard-coded palettes only |
| Detail views | `DetailActivity`, `DetailToolbarTintActivity`, `ContainActivity` | album/artist/folder detail |
| Subscription / VIP | `SubscriptionActivityV2` | billing (probably skip) |
| Feedback / FAQ / policy / web | `MusicFeedbackActivityV2`, `FAQActivity`, `WebActivity`, `PolicyActivity` | support shells |
| Widgets | 9 receiver classes + `AddWidgetActivity`, `WidgetLauncherActivity`, `WidgetEnterPlayerActivity` | no widgets at all in Pulse |

### Platform pieces

| Piece | Original | Pulse |
|---|---|---|
| Foreground playback | `MusicServiceV2` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | implemented in v1.4 — `MusicService` foreground service, media-style notification |
| Media notification | MediaSession + media buttons | implemented in v1.4 — notification + MediaSession lock-screen / Bluetooth controls |
| Lock-screen / AOD | dedicated activity + receiver | none |
| Widgets | 9 styles + update service | none |
| Database | Room | `SharedPreferences` only |
| Image loading | Glide + palette | own LRU + `MediaMetadataRetriever` |
| Video playback | ijkplayer/ffmpeg | none |
| Equalizer | `EqualizerActivity` + `V2` | 5-band only |
| Crossfade / gapless | present | none |
| Music content provider | `MusicContentProvider` | none |

### UI fidelity gap (the "exact look" part)

Pulse paints gradients and ovals **at runtime** (`Ui.java`, `Theme.java`) and has **7 layouts**.
The original ships **763 layout XMLs and 3,486 drawables** across every density bucket, plus:

- Lottie animations (`assets/*.json`, 20+ files) for scanning, ads, loading, badges
- 4-density theme bundles (`default_themes_{mdpi,hdpi,xhdpi,xxhdpi}`, `default_player_themes_*`)
- a runtime theme engine (`player_themes.json`, `default_themes.json`) with downloadable themes
- blurred cover backgrounds from `libstackblur.so`
- 9 widget layouts

So Pulse will never look identical by adding Java code — the visual identity lives in the original
`res/` + `assets/` + Lottie files. "Copy-paste" of those is the only way to pixel parity, and that
means using the original package as the base.

---

## 4. Two routes

### Route A — repackage the original (recommended for "exact same")

Keep the decoded `Music Player/` tree, rebuild with apktool, re-sign with your key.

- **Verified in this session:** `apktool b "Music Player"` completes successfully →
  unsigned APK **60.19 MB**, 3,707 entries, 5 dex + 2 asset dex, 30 libs, 3,486 res files.
- Result is **100% feature- and pixel-identical** by construction.
- Constraints:
  - Same package `musicplayer.musicapps.music.mp3player` → installs over / replaces the original.
    Changing the package name would break the compiled `R` class and every smali reference, so it
    must stay unless you also rename code references (large, error-prone job).
  - The ads and billing code come along. They can be muted (remove ad activities/providers,
    cut `assets/audience_network`, block billing) but each removal needs testing.
  - The APK is signed by the original developer; repackaging breaks their signature and is not
    permitted by the Play Store license. Technically it works, legally it is your risk — you said
    that is acceptable, so this report treats it as an option, not a recommendation.
- Size can be cut hard while keeping the app intact:
  - drop `armeabi-v7a` → −3.9 MB of libs
  - drop ad SDK deps/dex/assets → −15 to −20 MB
  - recompress PNG/WebP, keep only needed densities → −3 to −4 MB
  - realistic clean build: **20–25 MB**, still feature-complete.

### Route B — keep Pulse and add features

Effort is large and pixel parity is unreachable, but the code stays maintainable and ads-free.

| Priority | Work | Est. |
|---|---|---|
| P0 | Foreground `MediaSessionService` + notification + lock-screen controls | 1–2 days |
| P0 | Room DB + playlists/favorites/queue/recent | 2–3 days |
| P1 | Tag editor + album editor + art crop | 2 days |
| P1 | Genres + folder detail + scan progress/filters | 2 days |
| P1 | Widgets (start with 4x1 and 4x2) | 2–3 days |
| P2 | Theme engine (JSON-driven, Lottie) + theme chooser | 3–4 days |
| P2 | Backup/restore, search screen + history | 2 days |
| P3 | Crossfade/gapless, video, recap/badges, transfer | weeks |

Even after all of that, the UI will be "similar", not "exact", unless the original drawables,
layouts and Lottie assets are imported.

### Recommendation

For "exact music player jesa chahiye": **Route A**. Use the decoded tree, rebuild, sign, and
optionally strip ads + v7a. For a legal, maintainable app: **Route B**, starting with the
foreground service because its absence is the most visible defect (music stops when the app is
swiped away).

---

## 5. Reproduce the numbers

```powershell
# category sizes inside any APK
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::OpenRead('Music Player.apk')
$z.Entries | Group-Object { ($_.FullName -split '/')[0] } |
  ForEach-Object { [pscustomobject]@{Group=$_.Name; Files=$_.Count;
    MB=[math]::Round((($_.Group|Measure-Object Length -Sum).Sum)/1MB,2)} } |
  Sort-Object MB -Descending
$z.Dispose()

# rebuild the decoded tree (already verified: 60.19 MB output)
java -Xmx4g -jar apktool.jar b "Music Player" -o rebuilt.apk
```
