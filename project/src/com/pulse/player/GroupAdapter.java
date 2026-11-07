package com.pulse.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Simple two-line rows used by the Albums and Artists tabs. */
public class GroupAdapter extends BaseAdapter {

    public static class Group {
        public final String title;
        public final String subtitle;
        public final List<Song> songs = new ArrayList<>();
        /** Optional explicit badge value; -1 falls back to songs.size(). */
        public int count = -1;

        public Group(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final Context context;
    private final List<Group> groups;
    private Theme theme;

    public GroupAdapter(Context context, List<Group> groups, Theme theme) {
        this.context = context;
        this.groups = groups;
        this.theme = theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return groups.size();
    }

    @Override
    public Group getItem(int position) {
        return groups.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(context).inflate(R.layout.item_group, parent, false);
        }
        Group group = groups.get(position);
        TextView title = row.findViewById(R.id.groupTitle);
        TextView subtitle = row.findViewById(R.id.groupSubtitle);
        TextView badge = row.findViewById(R.id.groupBadge);

        title.setText(group.title);
        title.setTextColor(theme.text);
        subtitle.setText(group.subtitle);
        subtitle.setTextColor(theme.textDim);
        badge.setText(String.valueOf(group.count >= 0 ? group.count : group.songs.size()));
        badge.setTextColor(theme.accent);
        badge.setBackground(Ui.round((theme.accent & 0x00FFFFFF) | 0x22000000, 20, theme.accent));
        return row;
    }
}
