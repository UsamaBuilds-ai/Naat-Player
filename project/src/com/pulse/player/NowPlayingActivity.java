package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends Activity implements PlayerEngine.Listener {

    private static final int SWIPE_DP = 60;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<TextView> lyricViews = new ArrayList<>();

    private Theme theme;
    private PlayerEngine engine;
    private AlbumArtLoader artLoader;

    private TextView title;
    private TextView subtitle;
    private TextView posText;
    private TextView durText;
    private TextView btnPlay;
    private TextView btnShuffle;
    private TextView btnRepeat;
    private TextView btnTimer;
    private TextView btnFav;
    private TextView btnQueue;
    private TextView lyricsStatus;
    private TextView btnLanguage;
    private TextView lyricsHint;

    private DiscArtView disc;
    private View scrim;
    private View lyricsSheet;
    private SeekBar seekBar;
    private ScrollView lyricsScroll;
    private LinearLayout lyricsBox;

    private LyricsLoader.Lyrics lyrics;
    private long lyricsSongId = Long.MIN_VALUE;
    private String lyricsLang = "";
    private int activeLine = -1;
    private boolean dragging;
    private boolean sheetOpen;

    private float downX;
    private float downY;
    private boolean gestureAllowed;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            updateLyricHighlight();
            ui.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        engine = PlayerEngine.get();
        engine.attach(this);
        artLoader = new AlbumArtLoader(this);

        bindViews();
        paintTheme();
        wire();
        renderTrack();
        loadLyrics();
        paintModes();
    }

    private void bindViews() {
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        posText = findViewById(R.id.posText);
        durText = findViewById(R.id.durText);
        btnPlay = findViewById(R.id.btnPlay);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnTimer = findViewById(R.id.btnTimer);
        btnFav = findViewById(R.id.btnFav);
        btnQueue = findViewById(R.id.btnQueue);
        lyricsStatus = findViewById(R.id.lyricsStatus);
        btnLanguage = findViewById(R.id.btnLanguage);
        lyricsHint = findViewById(R.id.lyricsHint);
        disc = findViewById(R.id.disc);
        scrim = findViewById(R.id.scrim);
        lyricsSheet = findViewById(R.id.lyricsSheet);
        seekBar = findViewById(R.id.seekBar);
        lyricsScroll = findViewById(R.id.lyricsScroll);
        lyricsBox = findViewById(R.id.lyricsBox);

        lyricsStatus.setVisibility(View.INVISIBLE);
    }

    private void wire() {
        btnTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimerDialog();
            }
        });
        btnFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Song song = engine.getCurrent();
                if (song == null) return;
                boolean added = Prefs.toggleFavorite(NowPlayingActivity.this, song.id);
                paintFavourite();
                android.widget.Toast.makeText(NowPlayingActivity.this,
                        added ? "Added to Favourites" : "Removed from Favourites",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        btnQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NowPlayingActivity.this, QueueActivity.class));
            }
        });
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.toggle();
            }
        });
        findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.next(1);
            }
        });
        findViewById(R.id.btnPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (engine.position() > 4000) engine.seekTo(0);
                else engine.next(-1);
            }
        });
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.setShuffle(!engine.isShuffle());
            }
        });
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.cycleRepeat();
            }
        });
        lyricsHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLyrics();
            }
        });
        btnLanguage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLanguageDialog();
            }
        });
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeLyrics();
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    int duration = engine.duration();
                    if (duration > 0) engine.seekTo((int) (duration * (progress / 1000f)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
            }
        });
    }

    private void paintTheme() {
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        getWindow().setNavigationBarColor(theme.bgTop);

        lyricsSheet.setBackground(Ui.card(theme.cardTop, theme.cardBottom, theme.cardStroke));
        btnTimer.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 24, theme.cardStroke));
        btnFav.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 24, theme.cardStroke));
        btnQueue.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 24, theme.cardStroke));
        btnShuffle.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 24, theme.cardStroke));
        btnRepeat.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 24, theme.cardStroke));
        findViewById(R.id.btnPrev).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 28, theme.cardStroke));
        findViewById(R.id.btnNext).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 28, theme.cardStroke));

        btnPlay.setBackground(Ui.oval(theme.accentSoft, theme.accent));
        btnPlay.setTextColor(theme.onAccent);
        disc.setDiscTheme(theme);
        btnLanguage.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 17, theme.cardStroke));
        btnLanguage.setTextColor(theme.accent);

        ((TextView) findViewById(R.id.npLabel)).setTextColor(theme.accent);
        ((TextView) findViewById(R.id.lyricsLabel)).setTextColor(theme.accent);
        ((View) findViewById(R.id.sheetHandle)).setBackground(
                Ui.round(theme.cardStroke, 4, 0));
        title.setTextColor(theme.text);
        subtitle.setTextColor(theme.textDim);
        posText.setTextColor(theme.textDim);
        durText.setTextColor(theme.textDim);
        lyricsStatus.setTextColor(theme.textDim);
        lyricsHint.setTextColor(theme.textDim);
        btnTimer.setTextColor(theme.text);
        btnQueue.setTextColor(theme.text);
        ((TextView) findViewById(R.id.btnPrev)).setTextColor(theme.accentSoft);
        ((TextView) findViewById(R.id.btnNext)).setTextColor(theme.accentSoft);

        Ui.tintSeekBar(seekBar, theme);
    }

    private void paintModes() {
        btnShuffle.setTextColor(engine.isShuffle() ? theme.accent : theme.textDim);
        int repeat = engine.getRepeatMode();
        btnRepeat.setText(repeat == PlayerEngine.REPEAT_ONE ? "↻1" : "↻");
        btnRepeat.setTextColor(repeat == PlayerEngine.REPEAT_OFF ? theme.textDim : theme.accent);

        boolean timerOn = engine.sleepRemainingMs() > 0 || engine.isStopAfterThisSong();
        btnTimer.setTextColor(timerOn ? theme.accent : theme.text);
    }

    private void renderTrack() {
        Song song = engine.getCurrent();
        btnPlay.setText(engine.isPlaying() ? "❚❚" : "▶");

        if (song == null) {
            title.setText(R.string.nothing_playing);
            subtitle.setText(R.string.tagline);
            disc.setArt(null);
            disc.setSpinning(false);
            paintFavourite();
            lyricsHint.setVisibility(View.GONE);
            return;
        }
        title.setText(song.title);
        String sub = song.artist;
        if (!song.album.isEmpty()) sub = sub.isEmpty() ? song.album : sub + " • " + song.album;
        subtitle.setText(sub);
        lyricsHint.setVisibility(View.VISIBLE);

        // Decode big enough for a full-width disc, never a recycled thumbnail.
        int px = Math.min(1440, Math.max(
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels));
        artLoader.load(song, px, disc);
        disc.setSpinning(engine.isPlaying());

        paintFavourite();
    }

    /** Heart button reflects the current song's favourite state. */
    private void paintFavourite() {
        Song song = engine.getCurrent();
        boolean favourite = song != null && Prefs.isFavorite(this, song.id);
        btnFav.setText(favourite ? "♥" : "♡");
        btnFav.setTextColor(favourite ? theme.accent : theme.text);
        btnFav.setBackground(Ui.round(favourite
                        ? ((theme.accent & 0x00FFFFFF) | 0x33000000)
                        : ((theme.accent & 0x00FFFFFF) | 0x1A000000),
                24, favourite ? theme.accent : theme.cardStroke));
    }

    // ------------------------------------------------------------ lyrics sheet

    private void openLyrics() {
        if (sheetOpen) return;
        sheetOpen = true;

        int fallback = getResources().getDisplayMetrics().heightPixels;
        float from = lyricsSheet.getHeight() > 0 ? lyricsSheet.getHeight() : fallback;

        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(220).start();

        lyricsSheet.setVisibility(View.VISIBLE);
        lyricsSheet.setTranslationY(from);
        lyricsSheet.animate().translationY(0f).setDuration(280).start();
    }

    private void closeLyrics() {
        if (!sheetOpen) return;
        sheetOpen = false;

        int fallback = getResources().getDisplayMetrics().heightPixels;
        float to = lyricsSheet.getHeight() > 0 ? lyricsSheet.getHeight() : fallback;

        scrim.animate().alpha(0f).setDuration(200).start();
        lyricsSheet.animate().translationY(to).setDuration(240)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        lyricsSheet.setVisibility(View.GONE);
                        scrim.setVisibility(View.GONE);
                    }
                }).start();
    }

    /**
     * Gestures are read from dispatchTouchEvent so they still work over
     * clickable children (the old implementation missed every swipe).
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                gestureAllowed = !insideSeekBar(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (gestureAllowed
                        && Math.abs(event.getRawX() - downX)
                        > Math.abs(event.getRawY() - downY) * 1.4f) {
                    gestureAllowed = false;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (gestureAllowed) {
                    float dy = event.getRawY() - downY;
                    float dx = event.getRawX() - downX;
                    if (Math.abs(dy) > dp(SWIPE_DP) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        if (dy < 0) {
                            if (!sheetOpen) openLyrics();
                        } else if (sheetOpen) {
                            if (canCloseSheetFrom()) closeLyrics();
                        } else {
                            finish();
                        }
                    }
                }
                gestureAllowed = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                gestureAllowed = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean insideSeekBar(MotionEvent event) {
        if (seekBar == null) return false;
        int[] location = new int[2];
        seekBar.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] - dp(14) && x <= location[0] + seekBar.getWidth() + dp(14)
                && y >= location[1] - dp(14) && y <= location[1] + seekBar.getHeight() + dp(14);
    }

    /** Swiping down closes the sheet, unless the user is reading scrolled lyrics. */
    private boolean canCloseSheetFrom() {
        if (lyricsScroll.getScrollY() <= 0) return true;
        int[] location = new int[2];
        lyricsSheet.getLocationOnScreen(location);
        return downY < location[1] + dp(120);
    }

    private void showLanguageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Lyrics language")
                .setItems(LyricsOnline.LANG_LABELS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.setLyricsLanguage(NowPlayingActivity.this,
                                LyricsOnline.LANG_CODES[which]);
                        paintLanguage();
                        lyricsSongId = Long.MIN_VALUE;
                        lyrics = null;
                        loadLyrics();
                    }
                })
                .show();
    }

    private void paintLanguage() {
        btnLanguage.setText(Prefs.lyricsLanguageLabel(this).toUpperCase(Locale.US) + " ▾");
    }

    // ------------------------------------------------------------------ lyrics

    private void loadLyrics() {
        final Song song = engine.getCurrent();
        if (song == null) {
            showLyricsMessage("Play a Naat to see its lyrics");
            return;
        }
        final String lang = Prefs.lyricsLanguage(this);
        if (song.id == lyricsSongId && lang.equals(lyricsLang)) return;

        lyricsSongId = song.id;
        lyricsLang = lang;
        lyrics = null;
        activeLine = -1;
        paintLanguage();
        showLyricsMessage("Loading lyrics…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final LyricsLoader.Lyrics loaded = LyricsLoader.load(NowPlayingActivity.this, song);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Song current = engine.getCurrent();
                        if (current != null && current.id == song.id) {
                            lyrics = loaded;
                            renderLyrics();
                        }
                    }
                });
            }
        }, "lyrics-loader").start();
    }

    private void showLyricsMessage(String message) {
        lyricsBox.removeAllViews();
        lyricViews.clear();
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(theme.textDim);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, toPx(24), 0, 0);
        lyricsBox.addView(tv);
        lyricsStatus.setText("");
        lyricsStatus.setVisibility(View.INVISIBLE);
    }

    private void renderLyrics() {
        lyricsBox.removeAllViews();
        lyricViews.clear();
        activeLine = -1;

        if (lyrics == null || lyrics.lines.isEmpty()) {
            showLyricsMessage("No lyrics found.\n\nAdd a .lrc file next to the Naat "
                    + "(naat.ur.lrc for Urdu), embed a USLT tag, or turn on online lyrics "
                    + "in Settings.");
            return;
        }

        String status = lyrics.synced ? "synced" : "plain";
        if (lyrics.source != null && !"local".equals(lyrics.source)
                && !"none".equals(lyrics.source)) {
            status = lyrics.source + " · " + status;
        }
        lyricsStatus.setText(status);
        lyricsStatus.setVisibility(View.VISIBLE);

        for (LyricsLoader.Line line : lyrics.lines) {
            TextView tv = new TextView(this);
            tv.setText(line.text.isEmpty() ? "♪" : line.text);
            tv.setTextSize(14);
            tv.setTextColor(theme.textDim);
            tv.setPadding(0, toPx(5), 0, toPx(5));
            tv.setLineSpacing(0f, 1.2f);
            lyricsBox.addView(tv);
            lyricViews.add(tv);
        }
        updateLyricHighlight();
    }

    private void updateLyricHighlight() {
        if (lyrics == null || !lyrics.synced || lyricViews.isEmpty()) return;
        int position = engine.position();
        int index = -1;
        for (int i = 0; i < lyrics.lines.size(); i++) {
            if (lyrics.lines.get(i).timeMs <= position) index = i;
            else break;
        }
        if (index == activeLine) return;

        if (activeLine >= 0 && activeLine < lyricViews.size()) {
            lyricViews.get(activeLine).setTextColor(theme.textDim);
            lyricViews.get(activeLine).setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        activeLine = index;
        if (index >= 0 && index < lyricViews.size()) {
            final TextView current = lyricViews.get(index);
            current.setTextColor(theme.accent);
            current.setTypeface(null, android.graphics.Typeface.BOLD);
            lyricsScroll.post(new Runnable() {
                @Override
                public void run() {
                    lyricsScroll.smoothScrollTo(0, Math.max(0, current.getTop() - toPx(60)));
                }
            });
        }
    }

    // ------------------------------------------------------------------ timer

    private void showTimerDialog() {
        final String[] options = {
                "Off", "Stop after this Naat", "10 minutes", "15 minutes", "30 minutes", "60 minutes"
        };
        new AlertDialog.Builder(this)
                .setTitle("Sleep timer")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        engine.stopAfterThisSong(false);
                        switch (which) {
                            case 0:
                                engine.cancelSleep();
                                break;
                            case 1:
                                engine.cancelSleep();
                                engine.stopAfterThisSong(true);
                                break;
                            case 2:
                                engine.setSleepMinutes(10);
                                break;
                            case 3:
                                engine.setSleepMinutes(15);
                                break;
                            case 4:
                                engine.setSleepMinutes(30);
                                break;
                            default:
                                engine.setSleepMinutes(60);
                                break;
                        }
                        paintModes();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------- life

    @Override
    protected void onStart() {
        super.onStart();
        if (Prefs.keepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        engine.addListener(this);
        ui.post(ticker);
        renderTrack();
        paintLanguage();
        loadLyrics();
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(ticker);
        engine.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        artLoader.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (sheetOpen) {
            closeLyrics();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.np_hold, R.anim.np_out);
    }

    @Override
    public void onPlayerChanged() {
        renderTrack();
        paintModes();
        loadLyrics();
    }

    private void updateProgress() {
        int duration = engine.duration();
        int position = engine.position();
        if (!dragging && duration > 0) {
            seekBar.setProgress((int) (1000L * position / duration));
        }
        posText.setText(format(position));
        durText.setText(format(duration));
        btnPlay.setText(engine.isPlaying() ? "❚❚" : "▶");
        disc.setSpinning(engine.isPlaying());

        long remaining = engine.sleepRemainingMs();
        if (remaining > 0) {
            btnTimer.setText(formatTimer(remaining));
            btnTimer.setTextSize(10);
        } else {
            btnTimer.setText(engine.isStopAfterThisSong() ? "1⏱" : "⏱");
            btnTimer.setTextSize(17);
        }
    }

    private String formatTimer(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private String format(int ms) {
        return MainActivity.formatTime(ms);
    }

    private int toPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
