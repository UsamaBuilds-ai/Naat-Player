# Naat Player — offline Naat & video player (Android)

Ek **original, from-scratch** Android music/nait player (~4,600 lines Java, bina Gradle, bina ads) jo decoded `Music Player.apk` ki feature list aur flow ko match karta hai — lekin uska code, layout XML, icons, images, sounds ya branding **copy nahi** kiya. Sab kuch likha gaya hai (copyright-safe). Behaviour/screens match karna legal hai, unki assets uthana nahi.

## Screens

| Screen | Kya hai |
|---|---|
| **Splash** | Brief animated Naat Player branding, phir library khulti hai |
| **Library** | Tabs: Naats / Albums / Artists / Folders / Videos, live search, count pill, theme (◐), hide-eye (◉), settings (⚙) |
| **Now Playing** | Bada album art, **animated equalizer visualizer**, title/artist/album, seek + timer, shuffle/repeat, lyrics, sleep timer, equalizer, **favourite (♥)**, **queue** |
| **Video player** | Local videos with native Android playback controls (formats device codecs par depend karte hain) |
| **Settings** | Scan library, Hidden music, show hidden items, dot-folders, theme, sort, equalizer, headphone pause, keep screen on, sleep timer, version, **export/import backup**, **clear recently played** |
| **Hidden naats** | Hidden naats + hidden folders, dono ki alag tab, tap = unhide |
| **Equalizer** | System 5-band EQ (audio session bound) + presets — settings save aur gaana badalne par apne aap lagte hain |
| **Theme dialog** | 4 palettes: Sky blue (default), Midnight violet, Sunset, Emerald |
| **Queue** | Live play queue — tap to play, long-press to remove, clear all |
| **Playlist** | Playlist details — play all, open/rename/delete playlist, remove naats |

## Features (v1.9)

- MediaStore Naat library (`IS_MUSIC != 0`), album art via `MediaMetadataRetriever` + LRU cache
- Search: Naat title / artist / album / folder name, plus video filename
- Tabs: Naats, Albums, Artists, Folders, Videos — group pe tap = us album/artist/folder ka filter
- **Animated Naat Player splash** on launch, phir library khulti hai
- Swipe up ya compact player tap se full Now Playing screen khulti hai
- **Animated equalizer visualizer** Now Playing par (audio animation, video playback nahi)
- **Local video playback** Videos tab se Android ke native player controls ke saath, alag media permission
- **Favourites (♥)**: Naat long-press → "Add to Favourites / Remove from Favourites"; heart chip library tabs par + heart button Now Playing par
- Naat long-press actions: rename + confirmed delete (Android permission maang sakta hai media file change karne se pehle)
- **Auto name** — file name audio title/artist se banata hai; user rename start karta hai, Android permission maang sakta hai
- **Hidden music** (original jaisa): Naat/poora folder long-press → Hide; header **◉ eye** button hidden items show/kare; Settings → Hidden naats list + Unhide; Show hidden items / Show dot-folders switches; hidden items library, search aur shuffle queue se nikal jate hain
- **Playlists**: create / add / open / rename / delete; per-playlist screen "Play all"; MainActivity par chip playlist list le jaati hai
- **Sort**: Naat name / date added / duration / album, ↑ ↓ (Settings → Sort naats)
- Shuffle, Repeat off / all / one
- **Lyrics**: sidecar `.lrc` (timestamped, auto-scroll + current line highlight) ya embedded ID3 `USLT` frame; kuch na ho to saaf message
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

Lokal build (Windows PowerShell, Android SDK + JDK chahiye):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
adb install -r NaatPlayer-1.9.apk
```

`build.ps1` aapt2 → javac → d8 → zipalign → apksigner chalata hai. Signing ke liye yeh apne aap `out/pulse-debug.keystore` generate kar leta hai (alias `pulse`, pass `android`). Apni release keystore use karne ke liye GitHub Actions secrets dekhein neeche.

## GitHub Actions — auto build + release + tag

Har `main` branch push par workflow chalti hai:

1. Android SDK + JDK setup
2. `build.ps1` se signed APK build hota hai
3. Auto tag banata hai: `v1.9-build-<run-number>`
4. GitHub Release create hoti hai jisme APK asset upload hoti hai

**Keys/secrets repo mein nahi daale jayenge.** Inhein apne GitHub repo ki **Settings → Secrets and variables → Actions** mein add karna hoga:

| Secret | Kya hai | Zaroori? |
|---|---|---|
| `KEYSTORE_BASE64` | Apni `.keystore` / `.jks` file ka base64 string | No — agar nahi diya to auto debug keystore use hoga |
| `KEYSTORE_PASSWORD` | Keystore ka password | No (debug keystore ke liye `android`) |
| `KEY_ALIAS` | Signing alias | No (debug keystore ke liye `pulse`) |
| `KEY_PASSWORD` | Key ka password | No (debug keystore ke liye `android`) |

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
NaatPlayer-1.9.apk        # signed build (root par release artifact)
NaatPlayer-1.7.apk        # purana build
```

## Limitations

- Background playback foreground service (`mediaPlayback`) + media notification se chalta hai.
- Equalizer device par available na ho to screen message dikhti hai (crash nahi).
- Lyrics sirf local `.lrc` / embedded tag se — internet se fetch nahi karta (privacy).

## License

MIT — dekhein `LICENSE`.