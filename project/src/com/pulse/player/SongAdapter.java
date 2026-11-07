package com.pulse.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class SongAdapter extends BaseAdapter {

    private final Context context;
    private final List<Song> songs;
    private final AlbumArtLoader artLoader;
    private final int artPx;
    private Theme theme;
    private long playingId = -1;

    public SongAdapter(Context context, List<Song> songs, AlbumArtLoader artLoader, Theme theme) {
        this.context = context;
        this.songs = songs;
        this.artLoader = artLoader;
        this.theme = theme;
        this.artPx = Math.round(38 * context.getResources().getDisplayMetrics().density);
    }

    public void setPlayingId(long id) {
        this.playingId = id;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    public Theme getTheme() {
        return theme;
    }

    @Override
    public int getCount() {
        return songs.size();
    }

    @Override
    public Song getItem(int position) {
        return songs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return songs.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
            Holder h = new Holder();
            h.art = row.findViewById(R.id.itemArt);
            h.artImage = row.findViewById(R.id.itemArtImg);
            h.artGlyph = row.findViewById(R.id.itemArtGlyph);
            h.title = row.findViewById(R.id.itemTitle);
            h.artist = row.findViewById(R.id.itemArtist);
            h.duration = row.findViewById(R.id.itemDuration);
            h.fav = row.findViewById(R.id.itemFav);
            row.setTag(h);
        }

        Holder h = (Holder) row.getTag();
        Song song = songs.get(position);
        boolean playing = song.id == playingId;

        h.art.setBackground(Ui.art(theme.cardTop, theme.accent, 12));
        h.artGlyph.setTextColor(theme.onAccent);
        h.title.setText(song.title);
        h.artist.setText(song.album == null || song.album.isEmpty() ? song.artist : song.album);
        h.title.setTextColor(playing ? theme.accent : theme.text);
        h.artist.setTextColor(theme.textDim);
        h.duration.setText(playing ? "PLAYING" : MainActivity.formatTime(song.durationMs));
        h.duration.setTextColor(playing ? theme.accent : theme.textDim);
        h.fav.setVisibility(Prefs.isFavorite(context, song.id) ? View.VISIBLE : View.GONE);
        h.fav.setTextColor(theme.accent);

        h.artImage.setTag(song.id);
        artLoader.load(song, artPx, h.artImage, h.artGlyph);

        return row;
    }

    private static class Holder {
        View art;
        ImageView artImage;
        TextView artGlyph;
        TextView title;
        TextView artist;
        TextView duration;
        TextView fav;
    }
}
