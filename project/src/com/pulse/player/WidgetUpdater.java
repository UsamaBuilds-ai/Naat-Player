package com.pulse.player;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

/** Pushes the current track onto placed widgets (4x1 and 4x2). */
public final class WidgetUpdater {

    private WidgetUpdater() {
    }

    /**
     * @param sizeHint 1 = 4x1, 2 = 4x2, 0 = update every placed widget
     */
    public static void update(Context context, int sizeHint) {
        if (context == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager == null) return;
        if (sizeHint == 0 || sizeHint == 4) {
            updateKind(context, manager, R.layout.widget_4x1, Widget4x1Provider.class, R.xml.widget_4x1_info);
        }
        if (sizeHint == 0 || sizeHint == 2) {
            updateKind(context, manager, R.layout.widget_4x2, Widget4x2Provider.class, R.xml.widget_4x2_info);
        }
    }

    public static void update(Context context, AppWidgetManager manager, int[] ids, int sizeHint) {
        int layout = sizeHint == 4 ? R.layout.widget_4x1 : R.layout.widget_4x2;
        for (int id : ids) updateId(context, manager, id, layout);
    }

    public static void update(Context context) {
        update(context, 0);
    }

    private static void updateKind(Context context, AppWidgetManager manager,
                                   int layout, Class<?> provider, int info) {
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
        for (int id : ids) updateId(context, manager, id, layout);
    }

    private static void updateId(Context context, AppWidgetManager manager, int id, int layout) {
        Song song = PlayerEngine.get().getCurrent();
        boolean playing = PlayerEngine.get().isPlaying();

        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setTextViewText(R.id.widgetTitle,
                song == null ? context.getString(R.string.nothing_playing) : song.title);
        views.setTextViewText(R.id.widgetArtist,
                song == null ? context.getString(R.string.tagline)
                        : (song.artist.isEmpty() ? song.album : song.artist));
        views.setTextViewText(R.id.widgetPlay, playing ? "❚❚" : "▶");

        Bitmap art = song == null ? null : Artwork.load(context, song, 176);
        if (art != null) {
            views.setImageViewBitmap(R.id.widgetArt, art);
        } else {
            views.setImageViewResource(R.id.widgetArt, R.drawable.widget_disc);
        }

        int duration = PlayerEngine.get().duration();
        int position = PlayerEngine.get().position();
        if (layout == R.layout.widget_4x2) {
            int progress = duration > 0 ? Math.min(1000, (int) (1000L * position / duration)) : 0;
            views.setProgressBar(R.id.widgetProgress, 1000, progress, false);
        }

        views.setOnClickPendingIntent(R.id.widgetPrev,
                servicePending(context, MusicService.ACTION_PREV, 2000 + id, id));
        views.setOnClickPendingIntent(R.id.widgetPlay,
                servicePending(context, MusicService.ACTION_PLAY_PAUSE, 3000 + id, id));
        views.setOnClickPendingIntent(R.id.widgetNext,
                servicePending(context, MusicService.ACTION_NEXT, 4000 + id, id));
        views.setOnClickPendingIntent(R.id.widgetArt,
                openPending(context, id));

        manager.updateAppWidget(id, views);
    }

    private static PendingIntent servicePending(Context context, String action, int requestCode,
                                                 int widgetId) {
        Intent intent = new Intent(context, MusicService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(context, requestCode, intent, flags);
    }

    private static PendingIntent openPending(Context context, int requestCode) {
        Intent intent = new Intent(context, NowPlayingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 5000 + requestCode, intent, flags);
    }
}
