package com.pulse.player;

import android.net.Uri;

public final class VideoItem {

    public final long id;
    public final String title;
    public final long durationMs;
    public final Uri uri;

    public VideoItem(long id, String title, long durationMs, Uri uri) {
        this.id = id;
        this.title = title;
        this.durationMs = durationMs;
        this.uri = uri;
    }
}
