package com.ipc.demo.set;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Browse local snapshots and recordings for one device.
 */
public class CameraAlbumActivity extends AppCompatActivity {

    private TextView tvEmpty;
    private TextView tvHint;
    private AlbumAdapter adapter;
    private String devId;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_album);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvEmpty = findViewById(R.id.tvEmpty);
        tvHint = findViewById(R.id.tvHint);
        File dir = IpcLocalMediaHelper.getDeviceMediaDir(this, devId);
        if (dir != null) {
            tvHint.setText(getString(R.string.ipc_album_hint, dir.getAbsolutePath()));
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new AlbumAdapter();
        adapter.setListener(item -> {
            Intent intent = new Intent(this, CameraAlbumPreviewActivity.class);
            intent.putExtra(IpcConstants.EXTRA_MEDIA_PATH, item.file.getAbsolutePath());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<IpcLocalMediaHelper.MediaItem> list = IpcLocalMediaHelper.listMedia(this, devId);
        adapter.submitList(list);
        boolean empty = list.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private interface OnItemClick {
        void onClick(IpcLocalMediaHelper.MediaItem item);
    }

    private final class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.Holder> {

        private final List<IpcLocalMediaHelper.MediaItem> data = new ArrayList<>();
        private OnItemClick listener;

        void setListener(OnItemClick listener) {
            this.listener = listener;
        }

        void submitList(List<IpcLocalMediaHelper.MediaItem> list) {
            data.clear();
            if (list != null) {
                data.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_album_media, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            IpcLocalMediaHelper.MediaItem item = data.get(position);
            holder.tvName.setText(timeFormat.format(new Date(item.lastModified)));
            if (item.type == IpcLocalMediaHelper.MediaType.VIDEO) {
                holder.tvBadge.setVisibility(View.VISIBLE);
                holder.tvBadge.setText(R.string.ipc_album_video);
                holder.ivThumb.setImageResource(R.drawable.bg_panel);
                holder.ivThumb.setScaleType(ImageView.ScaleType.CENTER);
            } else {
                holder.tvBadge.setVisibility(View.GONE);
                holder.ivThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                holder.ivThumb.setImageBitmap(BitmapFactory.decodeFile(item.file.getAbsolutePath()));
            }
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView ivThumb;
            final TextView tvBadge;
            final TextView tvName;

            Holder(@NonNull View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.ivThumb);
                tvBadge = itemView.findViewById(R.id.tvBadge);
                tvName = itemView.findViewById(R.id.tvName);
            }
        }
    }
}
