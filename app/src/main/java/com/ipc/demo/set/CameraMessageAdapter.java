package com.ipc.demo.set;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.thingclips.drawee.view.DecryptImageView;
import com.thingclips.smart.ipc.messagecenter.bean.CameraMessageBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Alarm / motion detection message list adapter.
 */
public class CameraMessageAdapter extends RecyclerView.Adapter<CameraMessageAdapter.Holder> {

    public interface OnItemListener {
        void onItemClick(CameraMessageBean bean);

        void onImageClick(CameraMessageBean bean);

        void onLongClick(CameraMessageBean bean);
    }

    private final LayoutInflater inflater;
    private final List<CameraMessageBean> data = new ArrayList<>();
    private OnItemListener listener;

    public CameraMessageAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setListener(OnItemListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CameraMessageBean> list) {
        data.clear();
        if (list != null) {
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void remove(CameraMessageBean bean) {
        if (data.remove(bean)) {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(inflater.inflate(R.layout.item_camera_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CameraMessageBean bean = data.get(position);
        holder.tvTime.setText(bean.getDateTime());
        holder.tvContent.setText(bean.getMsgTypeContent());
        bindImage(holder.ivSnapshot, bean.getAttachPics());
        String[] videos = bean.getAttachVideos();
        boolean hasVideo = videos != null && videos.length > 0 && !TextUtils.isEmpty(videos[0]);
        boolean hasPic = !TextUtils.isEmpty(bean.getAttachPics());
        if (hasVideo && hasPic) {
            holder.tvMediaHint.setVisibility(View.VISIBLE);
            holder.tvMediaHint.setText(R.string.ipc_msg_pic_and_video);
        } else if (hasVideo) {
            holder.tvMediaHint.setVisibility(View.VISIBLE);
            holder.tvMediaHint.setText(R.string.ipc_msg_has_video);
        } else if (hasPic) {
            holder.tvMediaHint.setVisibility(View.VISIBLE);
            holder.tvMediaHint.setText(R.string.ipc_msg_tap_image);
        } else {
            holder.tvMediaHint.setVisibility(View.GONE);
        }
        holder.ivSnapshot.setOnClickListener(v -> {
            if (listener != null && hasPic) {
                listener.onImageClick(bean);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(bean);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(bean);
            }
            return true;
        });
    }

    private void bindImage(DecryptImageView imageView, String attachPics) {
        if (TextUtils.isEmpty(attachPics)) {
            imageView.setImageURI((String) null);
            return;
        }
        if (attachPics.contains("@")) {
            int index = attachPics.lastIndexOf('@');
            String imageUrl = attachPics.substring(0, index);
            String key = attachPics.substring(index + 1);
            imageView.setImageURI(imageUrl, key.getBytes());
        } else {
            try {
                DraweeController controller = Fresco.newDraweeControllerBuilder()
                        .setUri(Uri.parse(attachPics))
                        .build();
                imageView.setController(controller);
            } catch (Exception e) {
                imageView.setImageURI((String) null);
            }
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvTime;
        final TextView tvContent;
        final TextView tvMediaHint;
        final DecryptImageView ivSnapshot;

        Holder(View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvMediaHint = itemView.findViewById(R.id.tvMediaHint);
            ivSnapshot = itemView.findViewById(R.id.ivSnapshot);
        }
    }
}
