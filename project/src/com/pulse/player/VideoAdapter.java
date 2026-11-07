package com.pulse.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VideoAdapter extends BaseAdapter {

    private final Context context;
    private final List<VideoItem> allVideos;
    private final List<VideoItem> visibleVideos = new ArrayList<>();
    private final Theme theme;

    public VideoAdapter(Context context, List<VideoItem> videos, Theme theme) {
        this.context = context;
        this.allVideos = videos;
        this.theme = theme;
        visibleVideos.addAll(videos);
    }

    public void filter(String query) {
        visibleVideos.clear();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (VideoItem video : allVideos) {
            if (needle.isEmpty() || video.title.toLowerCase(Locale.ROOT).contains(needle)) {
                visibleVideos.add(video);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return visibleVideos.size();
    }

    @Override
    public VideoItem getItem(int position) {
        return visibleVideos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView == null
                ? LayoutInflater.from(context).inflate(R.layout.item_group, parent, false)
                : convertView;
        VideoItem video = getItem(position);
        TextView title = row.findViewById(R.id.groupTitle);
        TextView subtitle = row.findViewById(R.id.groupSubtitle);
        TextView badge = row.findViewById(R.id.groupBadge);
        title.setText(video.title);
        title.setTextColor(theme.text);
        subtitle.setText("VIDEO");
        subtitle.setTextColor(theme.textDim);
        badge.setText(MainActivity.formatTime(video.durationMs));
        badge.setTextColor(theme.accent);
        badge.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x22000000,
                20, theme.accent));
        return row;
    }
}
