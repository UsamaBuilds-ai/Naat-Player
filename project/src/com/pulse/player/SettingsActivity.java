package com.pulse.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/** Settings screen: library scanning, hidden music, playback toggles, theme, sort. */
public class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 2001;
    private static final int REQ_IMPORT = 2002;

    private Theme theme;
    private LinearLayout rows;
    private int hiddenSongs;
    private int hiddenFolders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        theme = Theme.current(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rows = findViewById(R.id.rows);
        findViewById(R.id.root).setBackground(Ui.gradient(theme.bgTop, theme.bgBottom));
        getWindow().setStatusBarColor(theme.bgTop);
        rows.setBackground(null);

        findViewById(R.id.btnClose).setBackground(
                Ui.round((theme.accent & 0x00FFFFFF) | 0x1A000000, 20, theme.cardStroke));
        ((TextView) findViewById(R.id.btnClose)).setTextColor(theme.text);
        ((TextView) findViewById(R.id.headerTitle)).setTextColor(theme.text);
        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        hiddenSongs = Prefs.hiddenSongs(this).size();
        hiddenFolders = Prefs.hiddenFolders(this).size();
        buildRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hiddenSongs = Prefs.hiddenSongs(this).size();
        hiddenFolders = Prefs.hiddenFolders(this).size();
        buildRows();
    }

    private void buildRows() {
        rows.removeAllViews();

        section("Library");
        item("Scan library", "Re-read naats from device storage", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Song> loaded = Library.load(SettingsActivity.this);
                Prefs.markLibraryDirty(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this,
                        String.format(Locale.US, "%d naats scanned", loaded.size()),
                        Toast.LENGTH_SHORT).show();
            }
        });
        item("Hidden naats", hiddenSongs + " naats • " + hiddenFolders + " folders hidden",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this, HiddenActivity.class));
                    }
                });
        toggle("Show hidden items", "Include hidden naats and folders in the library",
                Prefs.showHidden(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.setShowHidden(SettingsActivity.this, checked);
                        Prefs.markLibraryDirty(SettingsActivity.this);
                    }
                });
        toggle("Show dot-folders", "List folders that start with a dot in the Folders tab",
                Prefs.showHiddenFiles(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.setShowHiddenFiles(SettingsActivity.this, checked);
                        Prefs.markLibraryDirty(SettingsActivity.this);
                    }
                });

        section("Appearance");
        item("Theme", theme.label, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        });
        item("Sort naats", Prefs.sortLabel(SettingsActivity.this), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortDialog();
            }
        });
        item("Equalizer", "5-band equalizer with presets", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, EqualizerActivity.class));
            }
        });

        section("Playback");
        toggle("Pause on headphone detach", "Stop the music when headphones are unplugged",
                Prefs.pauseWhenDetached(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.setPauseWhenDetached(SettingsActivity.this, checked);
                    }
                });
        toggle("Keep screen on", "Do not dim while the player is open",
                Prefs.keepScreenOn(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        Prefs.setKeepScreenOn(SettingsActivity.this, checked);
                    }
                });
        item("Sleep timer", sleepSummary(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimerDialog();
            }
        });

        section("Backup");
        item("Export backup", "Save favourites, playlists, recent and hidden items", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportBackup();
            }
        });
        item("Import backup", "Restore a backup file made by Naat", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmImport();
            }
        });
        item("Recently played", Usage.recent(this).size() + " naats tracked",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        confirmClearRecent();
                    }
                });

        section("About");
        item("Version", "Naat Player 1.9", null);
        item("Lyrics", "Read from .lrc files or embedded USLT tags", null);
    }

    private String sleepSummary() {
        PlayerEngine engine = PlayerEngine.get();
        if (engine.isStopAfterThisSong()) return "Stops after the current Naat";
        long remaining = engine.sleepRemainingMs();
        if (remaining > 0) {
            return String.format(Locale.US, "Stops in %d min", (remaining / 60000) + 1);
        }
        return "Off";
    }

    // ------------------------------------------------------------- rows

    private void section(String title) {
        TextView view = new TextView(this);
        view.setText(title.toUpperCase(Locale.US));
        view.setTextColor(theme.accent);
        view.setTextSize(10);
        view.setLetterSpacing(0.24f);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(Math.round(Ui.dp(6)), Math.round(Ui.dp(18)),
                Math.round(Ui.dp(6)), Math.round(Ui.dp(8)));
        rows.addView(view);
    }

    private View baseRow(String title, String summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Math.round(Ui.dp(14)), Math.round(Ui.dp(12)),
                Math.round(Ui.dp(14)), Math.round(Ui.dp(12)));
        row.setBackground(Ui.round((theme.cardTop & 0x00FFFFFF) | 0xB3000000, 16, theme.cardStroke));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(Ui.dp(6));
        row.setLayoutParams(params);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(theme.text);
        titleView.setTextSize(14);
        row.addView(titleView);

        if (summary != null && !summary.isEmpty()) {
            TextView summaryView = new TextView(this);
            summaryView.setText(summary);
            summaryView.setTextColor(theme.textDim);
            summaryView.setTextSize(11.5f);
            summaryView.setPadding(0, Math.round(Ui.dp(3)), 0, 0);
            row.addView(summaryView);
        }
        return row;
    }

    private void item(String title, String summary, View.OnClickListener listener) {
        View row = baseRow(title, summary);
        if (listener != null) row.setOnClickListener(listener);
        rows.addView(row);
    }

    private void toggle(String title, String summary, boolean checked,
                        CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(android.view.Gravity.CENTER_VERTICAL);
        wrapper.setBackground(Ui.round((theme.cardTop & 0x00FFFFFF) | 0xB3000000, 16, theme.cardStroke));
        wrapper.setPadding(Math.round(Ui.dp(14)), Math.round(Ui.dp(10)),
                Math.round(Ui.dp(10)), Math.round(Ui.dp(10)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Math.round(Ui.dp(6));
        wrapper.setLayoutParams(params);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(theme.text);
        titleView.setTextSize(14);
        column.addView(titleView);

        if (summary != null && !summary.isEmpty()) {
            TextView summaryView = new TextView(this);
            summaryView.setText(summary);
            summaryView.setTextColor(theme.textDim);
            summaryView.setTextSize(11.5f);
            summaryView.setPadding(0, Math.round(Ui.dp(3)), 0, 0);
            column.addView(summaryView);
        }
        wrapper.addView(column);

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.accent));
        toggle.setOnCheckedChangeListener(listener);
        wrapper.addView(toggle);

        rows.addView(wrapper);
    }

    // ------------------------------------------------------------- dialogs

    private void showThemeDialog() {
        final Theme[] all = Theme.ALL;
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
        new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Theme.select(SettingsActivity.this, all[which]);
                        recreate();
                    }
                })
                .show();
    }

    private void showSortDialog() {
        final String[] labels = {"Naat name", "Date added", "Duration", "Album"};
        new AlertDialog.Builder(this)
                .setTitle("Sort naats by")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.setSort(SettingsActivity.this, which, Prefs.sortDescending(SettingsActivity.this));
                        Prefs.markLibraryDirty(SettingsActivity.this);
                        buildRows();
                    }
                })
                .setNeutralButton(Prefs.sortDescending(this) ? "Order: ↓" : "Order: ↑",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Prefs.setSort(SettingsActivity.this, Prefs.sortMode(SettingsActivity.this),
                                        !Prefs.sortDescending(SettingsActivity.this));
                                Prefs.markLibraryDirty(SettingsActivity.this);
                                buildRows();
                            }
                        })
                .show();
    }

    private void showTimerDialog() {
        final String[] options = {"Off", "Stop after this Naat", "10 minutes",
                "15 minutes", "30 minutes", "45 minutes", "60 minutes"};
        final PlayerEngine engine = PlayerEngine.get();
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
                            case 5:
                                engine.setSleepMinutes(45);
                                break;
                            default:
                                engine.setSleepMinutes(60);
                                break;
                        }
                        buildRows();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------- backup

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "pulse-backup.json");
        try {
            startActivityForResult(intent, REQ_EXPORT);
        } catch (Exception ignored) {
            Toast.makeText(this, "No file manager available", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmImport() {
        new AlertDialog.Builder(this)
                .setTitle("Import backup?")
                .setMessage("Favourites, playlists, hidden items and recent naats "
                        + "will be replaced by the backup contents.")
                .setPositiveButton("Choose file", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        try {
                            startActivityForResult(intent, REQ_IMPORT);
                        } catch (Exception ignored) {
                            Toast.makeText(SettingsActivity.this,
                                    "No file manager available", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearRecent() {
        new AlertDialog.Builder(this)
                .setTitle("Clear recently played?")
                .setMessage("The Recent filter will be emptied. Favourites and playlists stay.")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Usage.clear(SettingsActivity.this);
                        buildRows();
                        Toast.makeText(SettingsActivity.this, "Recent list cleared",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_EXPORT) {
            try {
                OutputStream out = getContentResolver().openOutputStream(uri, "wt");
                if (out == null) throw new Exception("no stream");
                out.write(Backup.export(this).getBytes("UTF-8"));
                out.close();
                Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not write backup", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_IMPORT) {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("no stream");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
                in.close();

                Backup.restore(this, new String(buffer.toByteArray(), "UTF-8"));
                buildRows();
                Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not read that backup file",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
