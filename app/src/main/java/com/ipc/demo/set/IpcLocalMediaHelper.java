package com.ipc.demo.set;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local snapshot / record files under app external files dir: {@code <files>/<devId>/}.
 */
public final class IpcLocalMediaHelper {

    public enum MediaType {
        IMAGE,
        VIDEO
    }

    public static final class MediaItem {
        public final File file;
        public final MediaType type;
        public final long lastModified;

        MediaItem(File file, MediaType type) {
            this.file = file;
            this.type = type;
            this.lastModified = file.lastModified();
        }
    }

    private IpcLocalMediaHelper() {
    }

    /**
     * Directory used by live snapshot and local MP4 record.
     */
    @Nullable
    public static File getDeviceMediaDir(@NonNull Context context, @Nullable String devId) {
        if (TextUtils.isEmpty(devId)) {
            return null;
        }
        File root = context.getExternalFilesDir(null);
        if (root == null) {
            return null;
        }
        return new File(root, devId);
    }

    /**
     * Ensure device media directory exists.
     */
    @Nullable
    public static File ensureDeviceMediaDir(@NonNull Context context, @Nullable String devId) {
        File dir = getDeviceMediaDir(context, devId);
        if (dir == null) {
            return null;
        }
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * List jpg / mp4 under device dir, newest first.
     */
    @NonNull
    public static List<MediaItem> listMedia(@NonNull Context context, @Nullable String devId) {
        File dir = getDeviceMediaDir(context, devId);
        if (dir == null || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<MediaItem> list = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.US);
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                list.add(new MediaItem(file, MediaType.IMAGE));
            } else if (name.endsWith(".mp4")) {
                list.add(new MediaItem(file, MediaType.VIDEO));
            }
        }
        Collections.sort(list, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem a, MediaItem b) {
                return Long.compare(b.lastModified, a.lastModified);
            }
        });
        return list;
    }

    public static boolean isImagePath(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
    }

    public static boolean isVideoPath(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        return path.toLowerCase(Locale.US).endsWith(".mp4");
    }
}
