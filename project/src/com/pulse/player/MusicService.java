package com.pulse.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Keeps playback alive while the app is in the background and publishes the
 * media notification plus the lock-screen / Bluetooth controls.
 */
public class MusicService extends Service implements PlayerEngine.Listener {

    public static final String ACTION_PLAY_PAUSE = "com.pulse.player.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.pulse.player.NEXT";
    public static final String ACTION_PREV = "com.pulse.player.PREV";
    public static final String ACTION_STOP = "com.pulse.player.STOP";
    public static final String ACTION_SEEK = "com.pulse.player.SEEK";
    public static final String EXTRA_POSITION = "position";

    private static final String CHANNEL_ID = "pulse_playback";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private PlayerEngine engine;
    private MediaSession session;
    private boolean foreground;
    private Bitmap artwork;
    private long artworkSongId = Long.MIN_VALUE;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (engine != null) {
                engine.savePlaybackState();
                if (engine.isPlaying()) publishState();
            }
            ui.postDelayed(this, 1000);
        }
    };

    /** Starts (or reuses) the service. Safe to call from any screen. */
    public static void ensure(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MusicService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = PlayerEngine.get();
        engine.attach(this);
        engine.addListener(this);

        createChannel();

        session = new MediaSession(this, "NaatPlayer");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (!engine.isPlaying()) engine.toggle();
            }

            @Override
            public void onPause() {
                if (engine.isPlaying()) engine.toggle();
            }

            @Override
            public void onSkipToNext() {
                engine.next(1);
            }

            @Override
            public void onSkipToPrevious() {
                if (engine.position() > 4000) engine.seekTo(0);
                else engine.next(-1);
            }

            @Override
            public void onSeekTo(long position) {
                engine.seekTo((int) position);
            }

            @Override
            public void onStop() {
                engine.pause();
                stopSelf();
            }
        });
        session.setActive(true);

        ui.post(progressTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null && engine.getCurrent() == null) {
            engine.restorePlayback(Library.load(this), false);
        }
        ensureForeground();

        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY_PAUSE.equals(action)) {
            engine.toggle();
        } else if (ACTION_NEXT.equals(action)) {
            engine.next(1);
        } else if (ACTION_PREV.equals(action)) {
            if (engine.position() > 4000) engine.seekTo(0);
            else engine.next(-1);
        } else if (ACTION_SEEK.equals(action)) {
            engine.seekTo(intent.getIntExtra(EXTRA_POSITION, 0));
        } else if (ACTION_STOP.equals(action)) {
            engine.pause();
            stopSelf();
            return START_NOT_STICKY;
        }

        publishAll();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPlayerChanged() {
        publishAll();
    }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(progressTick);
        engine.removeListener(this);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ output

    private void publishAll() {
        Song song = engine.getCurrent();
        publishMetadata(song);
        publishState();
        refreshArtwork(song);
        pushWidgets();

        Notification notification = buildNotification();
        if (notification == null) return;
        if (!foreground) {
            startForeground(NOTIFICATION_ID, notification);
            foreground = true;
        } else {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /** App Widgets are driven by the same publish path as the notification. */
    private void pushWidgets() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WidgetUpdater.update(MusicService.this);
            }
        }, "pulse-widgets").start();
    }

    private void publishMetadata(Song song) {
        if (session == null || song == null) return;
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, Math.max(0, engine.duration()));
        if (artwork != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork);
        }
        session.setMetadata(builder.build());
    }

    private void publishState() {
        if (session == null) return;
        int state;
        if (engine.isPlaying()) state = PlaybackState.STATE_PLAYING;
        else if (engine.getCurrent() != null) state = PlaybackState.STATE_PAUSED;
        else state = PlaybackState.STATE_NONE;

        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(state, Math.max(0, engine.position()), 1f);
        if (state == PlaybackState.STATE_PLAYING) {
            builder.setBufferedPosition(engine.position());
        }
        session.setPlaybackState(builder.build());
    }

    private void refreshArtwork(final Song song) {
        if (song == null) {
            artwork = null;
            artworkSongId = Long.MIN_VALUE;
            return;
        }
        if (song.id == artworkSongId) return;
        artworkSongId = song.id;
        artwork = null;

        final long requestedId = song.id;
        final Song target = song;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap loaded = Artwork.load(MusicService.this, target, 480);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Song current = engine.getCurrent();
                        if (current != null && current.id == requestedId) {
                            artwork = loaded;
                            publishMetadata(current);
                            NotificationManager manager = (NotificationManager)
                                    getSystemService(Context.NOTIFICATION_SERVICE);
                            Notification notification = buildNotification();
                            if (notification != null && manager != null) {
                                manager.notify(NOTIFICATION_ID, notification);
                            }
                        }
                    }
                });
            }
        }, "pulse-artwork").start();
    }

    private Notification buildNotification() {
        Song song = engine.getCurrent();
        if (song == null) return null;

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(song.title)
                .setContentText(song.artist.isEmpty() ? song.album : song.artist)
                .setContentIntent(openPlayer())
                .setDeleteIntent(serviceAction(ACTION_STOP, 9))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOngoing(engine.isPlaying());

        if (artwork != null) builder.setLargeIcon(artwork);

        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_previous, "Previous",
                serviceAction(ACTION_PREV, 1)));
        builder.addAction(new Notification.Action(
                engine.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                engine.isPlaying() ? "Pause" : "Play",
                serviceAction(ACTION_PLAY_PAUSE, 2)));
        builder.addAction(new Notification.Action(
                android.R.drawable.ic_media_next, "Next",
                serviceAction(ACTION_NEXT, 3)));

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(session == null ? null : session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);
        builder.setStyle(style);
        return builder.build();
    }

    private void ensureForeground() {
        if (foreground) return;
        Notification notification = buildNotification();
        if (notification == null) return;
        startForeground(NOTIFICATION_ID, notification);
        foreground = true;
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private PendingIntent openPlayer() {
        Intent intent = new Intent(this, NowPlayingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 7, intent, flags);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Playback",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Playback controls and current Naat");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }
}
