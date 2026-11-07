package com.pulse.player;

/** A single audio track found in the device media store. */
public class Song {

    public final long id;
    public final long albumId;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final long dateAdded;
    public final String path;

    public Song(long id, long albumId, String title, String artist, String album,
                long durationMs, long dateAdded, String path) {
        this.id = id;
        this.albumId = albumId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationMs = durationMs;
        this.dateAdded = dateAdded;
        this.path = path;
    }

    public String folder() {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "";
    }

    public String folderName() {
        String folder = folder();
        int slash = folder.lastIndexOf('/');
        return slash >= 0 ? folder.substring(slash + 1) : folder;
    }

    public boolean matches(String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return true;
        return title.toLowerCase().contains(q)
                || artist.toLowerCase().contains(q)
                || album.toLowerCase().contains(q)
                || folderName().toLowerCase().contains(q);
    }
}
