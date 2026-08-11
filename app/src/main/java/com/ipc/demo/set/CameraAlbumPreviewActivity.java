package com.ipc.demo.set;

import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/**
 * Full-screen preview for a local snapshot or recording file.
 */
public class CameraAlbumPreviewActivity extends AppCompatActivity {

    private VideoView videoPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_album_preview);

        String path = getIntent().getStringExtra(IpcConstants.EXTRA_MEDIA_PATH);
        if (TextUtils.isEmpty(path) || !new File(path).isFile()) {
            Toast.makeText(this, R.string.ipc_album_file_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        TextView tvTitle = findViewById(R.id.tvTitle);
        ImageView ivPreview = findViewById(R.id.ivPreview);
        videoPreview = findViewById(R.id.videoPreview);
        tvTitle.setText(new File(path).getName());

        if (IpcLocalMediaHelper.isImagePath(path)) {
            ivPreview.setVisibility(View.VISIBLE);
            videoPreview.setVisibility(View.GONE);
            ivPreview.setImageBitmap(BitmapFactory.decodeFile(path));
        } else if (IpcLocalMediaHelper.isVideoPath(path)) {
            ivPreview.setVisibility(View.GONE);
            videoPreview.setVisibility(View.VISIBLE);
            MediaController controller = new MediaController(this);
            controller.setAnchorView(videoPreview);
            videoPreview.setMediaController(controller);
            videoPreview.setVideoURI(Uri.fromFile(new File(path)));
            videoPreview.setOnPreparedListener(MediaPlayer::start);
            videoPreview.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, R.string.ipc_album_play_fail, Toast.LENGTH_SHORT).show();
                return true;
            });
        } else {
            Toast.makeText(this, R.string.ipc_album_file_missing, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (videoPreview != null && videoPreview.isPlaying()) {
            videoPreview.stopPlayback();
        }
    }
}
