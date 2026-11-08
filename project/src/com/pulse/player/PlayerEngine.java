package com.pulse.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One playback engine shared by every screen, so music keeps playing when the
 * user moves between the library and the now-playing screen.
 */
public class PlayerEngine {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String PLAYBACK_PREFS = "pulse_playback";

    private static PlayerEngine instance;

    public static synchronized PlayerEngine get() {
        if (instance == null) instance = new PlayerEngine();
        return instance;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<Song> queue = new ArrayList<>();
    private final Random random = new Random();

    private Context context;
    private AudioManager audioManager;
    private BroadcastReceiver noisyReceiver;

    private MediaPlayer player;
    private MediaPlayer fadingOut;
    private Song current;
    private boolean shuffle;
    private int repeatMode = REPEAT_ALL;
    private boolean restoreAttempted;
    private boolean stopAfterSong;
    private long sleepDeadline;

    private Equalizer equalizer;
    private boolean eqEnabled = true;

    private final Runnable sleepTask = new Runnable() {
        @Override
        public void run() {
            sleepDeadline = 0;
            if (player != null && player.isPlaying()) player.pause();
            notifyChanged();
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                    if (player == null) return;
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        if (player.isPlaying()) {
                            player.pause();
                            persistPlaybackState();
                            notifyChanged();
                        }
                    }
                }
            };

    private PlayerEngine() {
    }

    public void attach(Context anyContext) {
        if (context == null) {
            context = anyContext.getApplicationContext();
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            registerNoisyReceiver();
        }
    }

    /** Stops playback when headphones are unplugged, if the user enabled that option. */
    private void registerNoisyReceiver() {
        if (noisyReceiver != null || context == null) return;
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
                if (!Prefs.pauseWhenDetached(ctx)) return;
                if (player != null && player.isPlaying()) {
                    player.pause();
                    notifyChanged();
                }
            }
        };
        context.registerReceiver(noisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) listener.onPlayerChanged();
            }
        });
    }

    // ------------------------------------------------------------------ queue

    public void setQueue(List<Song> songs) {
        queue.clear();
        queue.addAll(songs);
        persistPlaybackState();
    }

    public List<Song> getQueue() {
        return queue;
    }

    public Song getCurrent() {
        return current;
    }

    public void updateSong(Song updated) {
        if (updated == null) return;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).id == updated.id) queue.set(i, updated);
        }
        if (current != null && current.id == updated.id) current = updated;
        persistPlaybackState();
        notifyChanged();
    }

    public void removeSong(long id) {
        boolean removedCurrent = current != null && current.id == id;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (queue.get(i).id == id) queue.remove(i);
        }
        if (removedCurrent) {
            if (queue.isEmpty()) {
                release();
                current = null;
                persistPlaybackState();
                notifyChanged();
            } else {
                play(queue.get(0));
            }
            return;
        }
        persistPlaybackState();
        notifyChanged();
    }

    public void restorePlayback(List<Song> songs, boolean ensureService) {
        if (restoreAttempted || current != null || context == null) return;
        SharedPreferences saved = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE);
        long currentId = saved.getLong("current_id", -1);
        String queueIds = saved.getString("queue_ids", "");
        if (currentId < 0 && queueIds.isEmpty()) {
            restoreAttempted = true;
            return;
        }
        if (songs.isEmpty()) return;

        Map<Long, Song> songsById = new HashMap<>();
        for (Song song : songs) songsById.put(song.id, song);
        queue.clear();
        if (!queueIds.isEmpty()) {
            for (String id : queueIds.split(",")) {
                try {
                    Song song = songsById.get(Long.parseLong(id));
                    if (song != null) queue.add(song);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        shuffle = saved.getBoolean("shuffle", false);
        repeatMode = saved.getInt("repeat_mode", REPEAT_ALL);
        if (repeatMode < REPEAT_OFF || repeatMode > REPEAT_ONE) repeatMode = REPEAT_ALL;
        Song song = songsById.get(currentId);
        restoreAttempted = true;
        if (song == null) {
            persistPlaybackState();
            return;
        }
        if (queue.indexOf(song) < 0) queue.add(0, song);
        startSong(song, saved.getBoolean("playing", false),
                saved.getInt("position", 0), false, ensureService);
    }

    public void savePlaybackState() {
        persistPlaybackState();
    }

    // --------------------------------------------------------------- playback

    public void play(Song song) {
        startSong(song, true, 0, true, true);
    }

    private void startSong(final Song song, boolean startImmediately, int position,
                           boolean recordUsage, boolean ensureService) {
        if (song == null) return;

        // Crossfade only on explicit play requests that start immediately;
        // restore-playback and error recovery stay instant.
        boolean fade = context != null && Prefs.crossfade(context)
                && startImmediately && player != null;

        MediaPlayer oldPlayer = player;
        player = null;

        if (fade && oldPlayer != null) {
            if (fadingOut != null) {
                try {
                    fadingOut.stop();
                } catch (Throwable ignored) {
                }
                fadingOut.release();
            }
            fadingOut = oldPlayer;
            fadeVolume(oldPlayer, 1f, 0f, FADE_MS, true);
        } else {
            if (fadingOut != null) {
                try {
                    fadingOut.stop();
                } catch (Throwable ignored) {
                }
                fadingOut.release();
                fadingOut = null;
            }
            if (oldPlayer != null) {
                try {
                    oldPlayer.stop();
                } catch (Throwable ignored) {
                }
                oldPlayer.release();
            }
        }
        releaseEqualizer();

        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onTrackFinished();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if (player == mp) {
                    release();
                    current = null;
                    persistPlaybackState();
                }
                notifyChanged();
                return true;
            }
        });

        try {
            player.setDataSource(song.path);
            player.prepare();
            if (position > 0) player.seekTo(position);
            if (fade) player.setVolume(0f, 0f);
            if (startImmediately) player.start();
        } catch (Exception e) {
            release();
            current = null;
            persistPlaybackState();
            notifyChanged();
            return;
        }

        if (fade && player != null) fadeVolume(player, 0f, 1f, FADE_MS, false);

        current = song;
        if (startImmediately) requestFocus();
        if (context != null) {
            if (recordUsage) Usage.markPlayed(context, song.id);
            if (startImmediately && ensureService) MusicService.ensure(context);
        }
        applyEqualizer();
        persistPlaybackState();
        notifyChanged();
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            persistPlaybackState();
            notifyChanged();
        }
    }

    public void toggle() {
        if (player == null) {
            if (!queue.isEmpty()) play(queue.get(0));
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.start();
            requestFocus();
            if (context != null) MusicService.ensure(context);
        }
        persistPlaybackState();
        notifyChanged();
    }

    public void playAt(int index) {
        if (index >= 0 && index < queue.size()) play(queue.get(index));
    }

    public void addToQueue(Song song) {
        if (song == null) return;
        queue.add(song);
        if (current == null && player == null) {
            // Nothing is playing yet: keep it queued but do not start audio.
            persistPlaybackState();
            notifyChanged();
            return;
        }
        persistPlaybackState();
        notifyChanged();
    }

    public void playNext(Song song) {
        if (song == null) return;
        int index = queue.indexOf(current);
        if (index >= 0) queue.add(index + 1, song);
        else queue.add(0, song);
        persistPlaybackState();
        notifyChanged();
    }

    public void removeFromQueue(int index) {
        if (index < 0 || index >= queue.size()) return;
        Song removed = queue.remove(index);
        if (removed == current) {
            if (queue.isEmpty()) {
                release();
                current = null;
                persistPlaybackState();
            } else {
                play(queue.get(Math.min(index, queue.size() - 1)));
                return;
            }
        }
        persistPlaybackState();
        notifyChanged();
    }

    public void clearQueue() {
        queue.clear();
        release();
        current = null;
        persistPlaybackState();
        notifyChanged();
    }

    public void next(int direction) {
        if (queue.isEmpty()) return;
        if (player == null) {
            play(queue.get(0));
            return;
        }

        int size = queue.size();
        int index = queue.indexOf(current);

        if (shuffle && size > 1) {
            int pick = random.nextInt(size);
            if (pick == index) pick = (pick + 1) % size;
            play(queue.get(pick));
            return;
        }

        int nextIndex;
        if (index < 0) {
            nextIndex = 0;
        } else {
            nextIndex = index + direction;
            if (nextIndex < 0) nextIndex = size - 1;
            if (nextIndex >= size) nextIndex = 0;
        }
        play(queue.get(nextIndex));
    }

    public void seekTo(int ms) {
        if (player != null) {
            try {
                player.seekTo(ms);
                persistPlaybackState();
                notifyChanged();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public int position() {
        try {
            return player == null ? 0 : player.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public int duration() {
        try {
            return player == null ? 0 : player.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    public int audioSessionId() {
        try {
            return player == null ? 0 : player.getAudioSessionId();
        } catch (Exception e) {
            return 0;
        }
    }

    // --------------------------------------------------------------- equalizer

    /** (Re)bind the equalizer to the current audio session and apply saved settings. */
    public void applyEqualizer() {
        int session = audioSessionId();
        if (session == 0 || context == null) {
            equalizer = null;
            return;
        }
        try {
            if (equalizer != null) {
                try {
                    equalizer.release();
                } catch (Throwable ignored) {
                }
                equalizer = null;
            }
            equalizer = new Equalizer(0, session);
            equalizer.setEnabled(isEqualizerEnabled());

            short[] saved = Prefs.equalizerBands(context);
            short count = equalizer.getNumberOfBands();
            short bands = (short) Math.min(count, saved == null ? 0 : saved.length);
            for (short b = 0; b < bands; b++) {
                equalizer.setBandLevel(b, saved[b]);
            }
            int preset = equalizerPreset();
            if (preset >= 0 && preset < equalizer.getNumberOfPresets()) {
                equalizer.usePreset((short) preset);
            }
        } catch (Throwable ignored) {
            equalizer = null;
        }
    }

    public boolean isEqualizerEnabled() {
        if (context != null) return Prefs.equalizerEnabled(context);
        return eqEnabled;
    }

    public void setEqualizerEnabled(Context context, boolean value) {
        Prefs.setEqualizerEnabled(context, value);
        this.eqEnabled = value;
        if (equalizer != null) {
            try {
                equalizer.setEnabled(value);
            } catch (Throwable ignored) {
            }
        }
    }

    public short getNumberOfBands() {
        return equalizer == null ? 0 : equalizer.getNumberOfBands();
    }

    public short getNumberOfPresets() {
        return equalizer == null ? 0 : equalizer.getNumberOfPresets();
    }

    public String getPresetName(short index) {
        return equalizer == null ? "" : equalizer.getPresetName(index);
    }

    public short[] getBandLevelRange() {
        return equalizer == null ? new short[]{0, 0} : equalizer.getBandLevelRange();
    }

    public short getEqualizerBand(short band) {
        if (equalizer == null) return 0;
        try {
            return equalizer.getBandLevel(band);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public void setEqualizerBand(short band, short level) {
        if (equalizer == null) return;
        try {
            equalizer.setBandLevel(band, level);
        } catch (Throwable ignored) {
            return;
        }
        if (context != null) {
            short[] saved = Prefs.equalizerBands(context);
            if (saved == null) saved = new short[getNumberOfBands()];
            if (band < saved.length) {
                saved[band] = level;
            } else {
                short[] grown = new short[band + 1];
                System.arraycopy(saved, 0, grown, 0, saved.length);
                grown[band] = level;
                saved = grown;
            }
            Prefs.setEqualizerBands(context, saved);
        }
    }

    public int equalizerPreset() {
        if (context != null) return Prefs.equalizerPreset(context);
        return 0;
    }

    public void setEqualizerPreset(Context context, int preset) {
        Prefs.setEqualizerPreset(context, preset);
        if (equalizer != null && preset >= 0 && preset < equalizer.getNumberOfPresets()) {
            try {
                equalizer.usePreset((short) preset);
            } catch (Throwable ignored) {
            }
        }
    }

    public void usePreset(short index) {
        if (equalizer == null) return;
        try {
            equalizer.usePreset(index);
        } catch (Throwable ignored) {
        }
        if (context != null) Prefs.setEqualizerPreset(context, index);
    }

    private void onTrackFinished() {
        if (stopAfterSong) {
            stopAfterSong = false;
            seekTo(0);
            notifyChanged();
            return;
        }
        if (repeatMode == REPEAT_ONE) {
            seekTo(0);
            if (player != null) player.start();
            notifyChanged();
            return;
        }
        int index = queue.indexOf(current);
        boolean lastTrack = index < 0 || index >= queue.size() - 1;
        if (lastTrack && repeatMode == REPEAT_OFF && !shuffle) {
            seekTo(0);
            notifyChanged();
            return;
        }
        next(1);
    }

    private void requestFocus() {
        if (audioManager != null) {
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void persistPlaybackState() {
        if (context == null) return;
        StringBuilder ids = new StringBuilder();
        for (Song song : queue) {
            if (ids.length() > 0) ids.append(',');
            ids.append(song.id);
        }
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE).edit()
                .putString("queue_ids", ids.toString())
                .putLong("current_id", current == null ? -1 : current.id)
                .putInt("position", position())
                .putBoolean("playing", isPlaying())
                .putBoolean("shuffle", shuffle)
                .putInt("repeat_mode", repeatMode)
                .apply();
    }

    public void release() {
        releaseEqualizer();
        if (fadingOut != null) {
            try {
                fadingOut.stop();
            } catch (Throwable ignored) {
            }
            fadingOut.release();
            fadingOut = null;
        }
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }

    private void releaseEqualizer() {
        if (equalizer == null) return;
        try {
            equalizer.release();
        } catch (Exception ignored) {
        }
        equalizer = null;
    }

    private static final long FADE_MS = 900L;

    private void fadeVolume(final MediaPlayer target, final float from, final float to,
                            final long durationMs, final boolean releaseWhenDone) {
        if (target == null) return;
        final long steps = Math.max(1, durationMs / 50);
        ui.postDelayed(new Runnable() {
            long step;

            @Override
            public void run() {
                step++;
                float t = Math.min(1f, step / (float) steps);
                float v = from + (to - from) * t;
                try {
                    target.setVolume(v, v);
                } catch (Throwable ignored) {
                }
                if (t < 1f) {
                    ui.postDelayed(this, 50);
                    return;
                }
                if (releaseWhenDone) {
                    try {
                        target.stop();
                    } catch (Throwable ignored) {
                    }
                    target.release();
                    if (fadingOut == target) fadingOut = null;
                }
            }
        }, 50);
    }

    // ------------------------------------------------------------------ modes

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean value) {
        shuffle = value;
        persistPlaybackState();
        notifyChanged();
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        persistPlaybackState();
        notifyChanged();
    }

    // ------------------------------------------------------- timer / sleep

    public void stopAfterThisSong(boolean value) {
        stopAfterSong = value;
        notifyChanged();
    }

    public boolean isStopAfterThisSong() {
        return stopAfterSong;
    }

    public void setSleepMinutes(int minutes) {
        ui.removeCallbacks(sleepTask);
        if (minutes <= 0) {
            sleepDeadline = 0;
        } else {
            sleepDeadline = System.currentTimeMillis() + minutes * 60000L;
            ui.postDelayed(sleepTask, minutes * 60000L);
        }
        notifyChanged();
    }

    public void cancelSleep() {
        ui.removeCallbacks(sleepTask);
        sleepDeadline = 0;
        notifyChanged();
    }

    public long sleepRemainingMs() {
        if (sleepDeadline == 0) return 0;
        return Math.max(0, sleepDeadline - System.currentTimeMillis());
    }
}
