package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends Activity implements PlayerEngine.Listener {

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
    private TextView bigArtGlyph;
    private TextView lyricsStatus;
    private ImageView bigArtImage;
    private View bigArt;
    private View lyricsCard;
    private TextView btnEq;
    private SeekBar seekBar;
    private ScrollView lyricsScroll;
    private LinearLayout lyricsBox;
    private EqualizerView eqVisualizer;

    private GestureDetector gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private LyricsLoader.Lyrics lyrics;
    private long lyricsSongId = Long.MIN_VALUE;
    private int activeLine = -1;
    private boolean dragging;

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
        artLoader = new AlbumArtLoader();

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
        bigArt = findViewById(R.id.bigArt);
        bigArtImage = findViewById(R.id.bigArtImg);
        bigArtGlyph = findViewById(R.id.bigArtGlyph);
        lyricsCard = findViewById(R.id.lyricsCard);
        lyricsStatus = findViewById(R.id.lyricsStatus);
        btnEq = findViewById(R.id.btnEq);
        seekBar = findViewById(R.id.seekBar);
        lyricsScroll = findViewById(R.id.lyricsScroll);
        lyricsBox = findViewById(R.id.lyricsBox);
        eqVisualizer = findViewById(R.id.eqVisualizer);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD) {
                    // horizontal swipe: ignore
                    return false;
                }
                if (diffY > SWIPE_THRESHOLD && velocityY > SWIPE_VELOCITY_THRESHOLD) {
                    finish();
                }
                return true;
            }
        });

        findViewById(R.id.root).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return false;
            }
        });

        paintTheme();
        wire();
        renderTrack();
        loadLyrics();
        paintModes();
    }

    private void wire() {
        btnEq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(NowPlayingActivity.this, EqualizerActivity.class));
            }
        });
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

        lyricsCard.setBackground(Ui.card(theme.cardTop, theme.cardBottom, theme.cardStroke));
        btnEq.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnTimer.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnFav.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnQueue.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        btnShuffle.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 23, theme.cardStroke));
        btnRepeat.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 23, theme.cardStroke));
        findViewById(R.id.btnPrev).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 26, theme.cardStroke));
        findViewById(R.id.btnNext).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 26, theme.cardStroke));

        btnPlay.setBackground(Ui.oval(theme.accentSoft, theme.accent));
        btnPlay.setTextColor(theme.onAccent);
        bigArt.setBackground(Ui.art(theme.cardTop, theme.accent, 28));
        bigArtGlyph.setTextColor(theme.onAccent);

        ((TextView) findViewById(R.id.npLabel)).setTextColor(theme.accent);
        ((TextView) findViewById(R.id.lyricsLabel)).setTextColor(theme.accent);
        title.setTextColor(theme.text);
        subtitle.setTextColor(theme.textDim);
        posText.setTextColor(theme.textDim);
        durText.setTextColor(theme.textDim);
        lyricsStatus.setTextColor(theme.textDim);
        btnTimer.setTextColor(theme.text);
        btnQueue.setTextColor(theme.text);
        btnEq.setTextColor(theme.text);
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
            bigArtImage.setImageDrawable(null);
            bigArtGlyph.setVisibility(View.VISIBLE);
            paintFavourite();
            eqVisualizer.setActive(false);
            return;
        }
        title.setText(song.title);
        String sub = song.artist;
        if (!song.album.isEmpty()) sub = sub.isEmpty() ? song.album : sub + " • " + song.album;
        subtitle.setText(sub);

        bigArt.setTag(song.id);
        int px = Math.round(250 * getResources().getDisplayMetrics().density);
        artLoader.load(song, px, bigArtImage, bigArtGlyph);
        paintFavourite();
        eqVisualizer.setActive(engine.isPlaying());
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
                20, favourite ? theme.accent : theme.cardStroke));
    }

    // ------------------------------------------------------------------ lyrics

    private void loadLyrics() {
        final Song song = engine.getCurrent();
        if (song == null) {
            showLyricsMessage("Play a Naat to see its lyrics");
            return;
        }
        if (song.id == lyricsSongId) return;
        lyricsSongId = song.id;
        lyrics = null;
        activeLine = -1;
        showLyricsMessage("Loading lyrics…");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final LyricsLoader.Lyrics loaded = LyricsLoader.load(song);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (engine.getCurrent() != null && engine.getCurrent().id == song.id) {
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
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(theme.textDim);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, toPx(18), 0, 0);
        lyricsBox.addView(tv);
        lyricsStatus.setText("");
    }

    private void renderLyrics() {
        lyricsBox.removeAllViews();
        lyricViews.clear();
        activeLine = -1;

        if (lyrics == null || lyrics.lines.isEmpty()) {
            showLyricsMessage("No lyrics found.\n\nAdd a .lrc file next to the Naat, "
                    + "or embed lyrics (USLT tag) in the audio file.");
            lyricsStatus.setText("none");
            return;
        }

        lyricsStatus.setText(lyrics.synced ? "synced" : "plain");
        for (LyricsLoader.Line line : lyrics.lines) {
            TextView tv = new TextView(this);
            tv.setText(line.text.isEmpty() ? "♪" : line.text);
            tv.setTextSize(14);
            tv.setTextColor(theme.textDim);
            tv.setPadding(0, toPx(4), 0, toPx(4));
            tv.setLineSpacing(0f, 1.15f);
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

    // ------------------------------------------------------------------ life

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

        long remaining = engine.sleepRemainingMs();
        if (remaining > 0) {
            btnTimer.setText(formatTimer(remaining));
            btnTimer.setTextSize(10);
        } else {
            btnTimer.setText(engine.isStopAfterThisSong() ? "1⏱" : "⏱");
            btnTimer.setTextSize(16);
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
}
