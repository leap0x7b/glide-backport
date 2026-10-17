package com.bumptech.glide.load.resource.bitmap;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * An {@link com.bumptech.glide.load.resource.bitmap.BitmapDecoder} that can decode a thumbnail frame
 * {@link Bitmap} from a {@link android.os.ParcelFileDescriptor} containing a video.
 *
 * <p>Video thumbnails require {@code android.media.MediaMetadataRetriever} (API 10+). On API 1-9
 * {@link #decode(ParcelFileDescriptor, BitmapPool, int, int, DecodeFormat)} throws
 * {@link IOException} so image loads continue to work; only video thumbnails are unavailable.
 */
public class VideoBitmapDecoder implements BitmapDecoder<ParcelFileDescriptor> {
    private static final String TAG = "VideoBitmapDecoder";
    private static final MediaMetadataRetrieverFactory DEFAULT_FACTORY =  new MediaMetadataRetrieverFactory();
    private static final int NO_FRAME = -1;
    private MediaMetadataRetrieverFactory factory;
    private int frame;

    public VideoBitmapDecoder() {
        this(DEFAULT_FACTORY, NO_FRAME);
    }

    public VideoBitmapDecoder(int frame) {
      this(DEFAULT_FACTORY, checkValidFrame(frame));
    }

    VideoBitmapDecoder(MediaMetadataRetrieverFactory factory) {
      this(factory, NO_FRAME);
    }

    VideoBitmapDecoder(MediaMetadataRetrieverFactory factory, int frame) {
        this.factory = factory;
        this.frame = frame;
    }

    @Override
    public Bitmap decode(ParcelFileDescriptor resource, BitmapPool bitmapPool, int outWidth, int outHeight,
            DecodeFormat decodeFormat)
            throws IOException {
        Object retriever;
        try {
            retriever = factory.build();
        } catch (Exception e) {
            throw new IOException("MediaMetadataRetriever not available (requires API 10+)", e);
        }
        if (retriever == null) {
            throw new IOException("MediaMetadataRetriever not available (requires API 10+)");
        }
        try {
            // setDataSource(FileDescriptor)
            retriever.getClass().getMethod("setDataSource", FileDescriptor.class)
                    .invoke(retriever, resource.getFileDescriptor());
            Bitmap result;
            if (frame >= 0) {
                result = (Bitmap) retriever.getClass().getMethod("getFrameAtTime", long.class)
                        .invoke(retriever, (long) frame);
            } else {
                result = (Bitmap) retriever.getClass().getMethod("getFrameAtTime").invoke(retriever);
            }
            try {
                retriever.getClass().getMethod("release").invoke(retriever);
            } catch (Exception e) {
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Failed to release MediaMetadataRetriever", e);
                }
            }
            resource.close();
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to decode video frame (requires API 10+)", e);
        }
    }

    @Override
    public String getId() {
        return "VideoBitmapDecoder.com.bumptech.glide.load.resource.bitmap";
    }

    // Visible for testing.
    // Uses Object instead of MediaMetadataRetriever so this class verifies on API 1-9 where that
    // framework class (added in API 10) does not exist. Instantiation happens reflectively.
    static class MediaMetadataRetrieverFactory {
        public Object build() throws Exception {
            return Class.forName("android.media.MediaMetadataRetriever").newInstance();
        }
    }

    private static int checkValidFrame(int frame) {
      if (frame < 0) {
        throw new IllegalArgumentException("Requested frame must be non-negative");
      }
      return frame;
    }
}
