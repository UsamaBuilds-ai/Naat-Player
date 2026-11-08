# Naat Player — offline Naat & video player (Android)

Ek **original, from-scratch** Android music/nait player (~4,600 lines Java, bina Gradle, bina ads) jo decoded `Music Player.apk` ki feature list aur flow ko match karta hai — lekin uska code, layout XML, icons, images, sounds ya branding **copy nahi** kiya. Sab kuch likha gaya hai (copyright-safe). Behaviour/screens match karna legal hai, unki assets uthana nahi.

## Screens

| Screen | Kya hai |
|---|---|
| **Splash** | Brief animated Naat Player branding, phir library khulti hai |
| **Library** | Tabs: Naats / Albums / Artists / Folders / Videos, live search, count pill, theme (◐), hide-eye (◉), settings (⚙) |
| **Now Playing** | CD-style rotating album art, title/artist/album, seek + timer, shuffle/repeat, **swipe-up lyric sheet**, sleep timer, **favourite (♥)**, **queue** |
| **Video player** | Local videos with native Android playback controls (formats device codecs par depend karte hain) |
| **Settings** | Scan library, Hidden music, show hidden items, dot-folders, theme, sort, equalizer, **lyrics language / online / Urdu translate**, headphone pause, keep screen on, sleep timer, version, **export/import backup**, **clear recently played** |
| **Hidden naats** | Hidden naats + hidden folders, dono ki alag tab, tap = unhide |
| **Equalizer** | System 5-band EQ (audio session bound) + presets — settings save aur gaana badalne par apne aap lagte hain |
| **Tag editor** | Title/artist/album/genre edit (MediaStore write request); long-press Naat → Edit tags; long-press Album → Edit album |
| **Theme dialog** | 4 colour cards + saved custom themes — naam nahi, sirf rang |
| **Queue** | Live play queue — tap to play, long-press to remove, clear all |
| **Playlist** | Playlist details — play all, open/rename/delete playlist, remove naats |

## Features (v2.1)

- MediaStore Naat library (`IS_MUSIC != 0`), album art via `MediaMetadataRetriever` + size-aware LRU cache
- **SQLite** single store — favourites, hidden naats/folders, rename overrides, playlists, recent playback; one-time import of the old prefs/JSON stores
- **Home-screen widgets** — 4x1 (art + transport) and 4x2 (bigger art + progress); pinned from Settings
- **Genres tab** — naats grouped by genre; tap to filter
- **Tag editor** — title/artist/album/genre per naat; album mode edits the whole album; user cover art stored app-privately and used everywhere
- **Custom themes** — pick background gradient + accent, everything else derived; save/delete your own themes; stock colour cards stay
- **Crossfade** — fade out the ending naat, fade in the next (toggleable in Settings)
- **Scan progress** — library scan in the background; a dialog appears only for slow scans
- **Recent searches** — last searches live under the search bar when it's empty; one tap to re-run
- Search: Naat title / artist / album / folder name, plus video filename
- Tabs: Naats, Albums, Artists, Folders, Videos — group pe tap = us album/artist/folder ka filter
- **Animated Naat Player splash** on launch, phir library khulti hai
- Swipe up ya compact player tap se full Now Playing screen khulti hai
- **Animated equalizer visualizer** Now Playing par (audio animation, video playback nahi)
- **Local video playback** Videos tab se Android ke native player controls ke saath, alag media permission
- **Favourites (♥)**: Naat long-press → "Add to Favourites / Remove from Favourites"; heart chip library tabs par + heart button Now Playing par
- Naat long-press actions: rename + confirmed delete (Android permission maang sakta hai media file change karne se pehle)
- **Auto name** — file name audio title/artist se banata hai; user rename start karta hai, Android permission maang sakta hai; naam restart ke baad bhi waise hi dikhta hai
- **Library quick modes** — search row mein **♥ My Favourite**, **◷ Recent**, **▤ Playlists**; alag chip row nahi
- **CD-style art**: art hai to wo round CD ki tarah ghoomta hai (play par spin, pause par ruka); art nahi hai to themed disc + `♪` glyph; art screen-width resolution par load hota hai
- **Theme chooser** ab sirf colour cards hai — naam nahi, sirf palette; selected card par accent border
- **Swipe up / down**: mini-player ya Now Playing khulne/band hone par poora screen slide hota hai
- **Hidden music** (original jaisa): Naat/poora folder long-press → Hide; header **◉ eye** button hidden items show/kare; Settings → Hidden naats list + Unhide; Show hidden items / Show dot-folders switches; hidden items library, search aur shuffle queue se nikal jate hain
- **Playlists**: create / add / open / rename / delete; per-playlist screen "Play all"; MainActivity par chip playlist list le jaati hai
- **Sort**: Naat name / date added / duration / album, ↑ ↓ (Settings → Sort naats)
- Shuffle, Repeat off / all / one
- **Lyrics**: swipe-up sheet (Naat play na ho tab tak message); order: `naat.<lang>.lrc` → `naat.lrc` → embedded ID3 `USLT` → cached online → online (**LRCLIB** primary, **lyrics.ovh** fallback); timestamped lines ka auto-scroll + highlight; kuch na mile to saaf message; Urdu default language, machine translation **sirf Urdu ke liye**
- **Lyrics language**: Settings → Lyrics language (Urdu, Hindi, English, Punjabi, Sindhi, Pashto, Arabic, Auto) + Settings → Online lyrics / Translate to Urdu
- **Sleep timer**: Off / "stop after this Naat" / 10 / 15 / 30 / 45 / 60 min (countdown chip)
- **Equalizer**: 5 bands + presets, current playback session se bandha
- **4 themes** runtime repaint (gradients code se bante hain → instant switch)
- **Pause on headphone detach** (`ACTION_AUDIO_BECOMING_NOISY`) + **Keep screen on**
- Ek shared `PlayerEngine` mein playback — library ↔ now playing switch karne par music chalta rehta hai
- Bottom mini-player Naat start hone ke baad aata hai; swipe up se Now Playing khulta hai. Queue + position save/restore hote hain agar Android process restart kare
- Audio focus handling, Android 13+ `READ_MEDIA_AUDIO` permission flow

## Abhi bhi missing (original app mein hai)

Genres tab, tag editor, lock-screen full player, widgets, crossfade/gapless, monthly recap/badges, device transfer, VIP/subscription, short-clip scan filters, search history, VLC-level codec coverage.

## Build

Lokal build (Windows PowerShell, Android SDK + JDK 17+ chahiye):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
adb install -r NaatPlayer-2.0.apk
```

Version override karne ke liye:

```powershell
$env:VERSION_NAME="2.0.0"; $env:VERSION_CODE="20000"
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
```

`build.ps1` aapt2 → javac → d8 → zipalign → apksigner chalata hai. Signing ke liye yeh apne aap `out/pulse-debug.keystore` generate kar leta hai (alias `pulse`, pass `android`). Release signing ke liye `KEYSTORE_PATH`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS` env vars set karein.

## GitHub Actions — CI + signed release

Do workflows hain:

| Workflow | Trigger | Kya karta hai |
|---|---|---|
| `ci.yml` | PR, `main` push, manual | Debug-signed APK build karke artifact upload karta hai (14 din) |
| `release.yml` | `v*` tag push, manual dispatch | Secrets se **release-signed** APK build, signature verify, tag par GitHub Release + APK + SHA256 upload |

Release nikalne ka tareeqa:

```powershell
git tag v2.0.0
git push origin v2.0.0
```

Ya GitHub → Actions → **Release - Signed APK** → *Run workflow* (version `1.9.0` dein).

### Zaroori secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Kya hai |
|---|---|
| `KEYSTORE_BASE64` | Release `.jks` file ka base64 (ek line) |
| `KEYSTORE_PASSWORD` | Keystore ka password |
| `KEY_ALIAS` | Key alias (e.g. `naatplayer`) |
| `KEY_PASSWORD` | Key ka password |

`release.yml` in char ke bagair fail ho jata hai (CI build ke liye keystore ki zaroorat nahi).

### Keystore ek dafa banayein (isse safe rakhein — kho gaya to app update nahi ho payega)

```powershell
keytool -genkeypair -v -keystore naatplayer-release.jks `
  -alias naatplayer -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=Naat Player, O=Naat, C=PK"

# base64 (Windows)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("naatplayer-release.jks")) | Set-Clipboard
```

```bash
# base64 (macOS/Linux)
base64 -i naatplayer-release.jks | tr -d '\n'
```

Clipboard wali value `KEYSTORE_BASE64` mein paste karein. `.jks` file kabhi commit na karein — `.gitignore` already block karta hai.


## Files

```
project/
  AndroidManifest.xml
  src/com/pulse/player/   # Splash, MainActivity, VideoPlayer, Settings, NowPlaying, Equalizer,
                          # PlayerEngine, Library, Prefs, LyricsLoader, AlbumArtLoader, Theme,
                          # Ui, Playlists, Usage, Backup, MusicService, Queue, Playlist, Artwork,
                          # adapters...
  res/                    # layouts, drawables, values
build.ps1                 # aapt2 + javac + d8 + zipalign + apksigner (no Gradle)
.github/workflows/ci.yml       # PR/main build
.github/workflows/release.yml  # tag v* par signed release + APK upload
```

## Limitations

- Background playback foreground service (`mediaPlayback`) + media notification se chalta hai.
- Equalizer device par available na ho to screen message dikhti hai (crash nahi).
- Online lyrics **LRCLIB** (no key, no rate limit) aur **lyrics.ovh** se — naat lyrics coverage limited hai, is liye local `.lrc` / embedded tag ab bhi sab se reliable hai. Machine translation (MyMemory free API, ~5000 chars/day anonymous) sirf Urdu ke liye, best-effort; poetry ka exact translation na mile to machine output se kaam chalana hoga.

## Credits

- **LRCLIB** (https://lrclib.net) — open lyrics database, MIT license (https://github.com/tranxuanthang/lrclib). Online lyrics primary source.
- **Circle-clip + rotation CD-art technique** — reimplemented in `DiscArtView.java` after André Mion's **Music-Cover-View** (Apache License 2.0, https://github.com/andremion/Music-Cover-View). No verbatim code was copied.
- **CircleImageView** — hdodenhof (Apache License 2.0), circle-crop reference.
- **QuickLyric / SongSync / FastLyrics** — GPL-3.0; used as behaviour reference only, not copied.

## License

MIT — dekhein `LICENSE`.