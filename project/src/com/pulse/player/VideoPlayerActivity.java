package com.pulse.player;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class VideoPlayerActivity extends Activity {

    public static final String EXTRA_URI = "video_uri";
    public static final String EXTRA_TITLE = "video_title";
    private static final String STATE_POSITION = "position";

    private VideoView videoView;
    private int restorePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String rawUri = getIntent().getStringExtra(EXTRA_URI);
        if (rawUri == null) {
            finish();
            return;
        }

        TextView title = findViewById(R.id.videoTitle);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        videoView = findViewById(R.id.videoView);
        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoView);
        videoView.setMediaController(controls);
        if (savedInstanceState != null) {
            restorePosition = savedInstanceState.getInt(STATE_POSITION);
        }
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer player) {
                if (restorePosition > 0) videoView.seekTo(restorePosition);
                videoView.start();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer player, int what, int extra) {
                Toast.makeText(VideoPlayerActivity.this,
                        "This video format is not supported on this device",
                        Toast.LENGTH_LONG).show();
                finish();
                return true;
            }
        });
        videoView.setVideoURI(Uri.parse(rawUri));
    }

    @Override
    protected void onPause() {
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
            restorePosition = videoView.getCurrentPosition();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (videoView != null) state.putInt(STATE_POSITION, videoView.getCurrentPosition());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) videoView.suspend();
        super.onDestroy();
    }
}
