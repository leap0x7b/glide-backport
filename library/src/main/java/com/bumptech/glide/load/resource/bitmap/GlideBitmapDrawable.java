package com.bumptech.glide.load.resource.bitmap;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;

import com.bumptech.glide.load.resource.drawable.GlideDrawable;

/**
 * A static {@link com.bumptech.glide.load.resource.drawable.GlideDrawable} for displaying a single image.
 */
public class GlideBitmapDrawable extends GlideDrawable {
    private final Rect destRect = new Rect();
    private int width;
    private int height;
    private boolean applyGravity;
    private boolean mutated;
    private BitmapState state;

    public GlideBitmapDrawable(Resources res, Bitmap bitmap) {
        this(res, new BitmapState(bitmap));
    }

    GlideBitmapDrawable(Resources res, BitmapState state) {
        if (state == null) {
            throw new NullPointerException("BitmapState must not be null");
        }

        this.state = state;
        final int targetDensity;
        if (res != null) {
            targetDensity = getDensityDpi(res);
            state.targetDensity = targetDensity;
        } else {
            targetDensity = state.targetDensity;
        }
        width = getScaledWidth(state.bitmap, targetDensity);
        height = getScaledHeight(state.bitmap, targetDensity);
    }

    // DisplayMetrics.densityDpi and DENSITY_DEFAULT require API 4. Fall back to 160 (mdpi).
    private static int getDensityDpi(Resources res) {
        try {
            final int density = res.getDisplayMetrics().densityDpi;
            return density == 0 ? 160 : density;
        } catch (NoSuchFieldError e) {
            return 160;
        } catch (Exception e) {
            return 160;
        }
    }

    // Bitmap.getScaledWidth/Height() require API 4. Fall back to getWidth()/getHeight() on API 1-3.
    private static int getScaledWidth(Bitmap bitmap, int targetDensity) {
        try {
            return bitmap.getScaledWidth(targetDensity);
        } catch (NoSuchMethodError e) {
            return bitmap.getWidth();
        }
    }

    private static int getScaledHeight(Bitmap bitmap, int targetDensity) {
        try {
            return bitmap.getScaledHeight(targetDensity);
        } catch (NoSuchMethodError e) {
            return bitmap.getHeight();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public boolean isAnimated() {
        return false;
    }

    @Override
    public void setLoopCount(int loopCount) {
        // Do nothing.
    }

    @Override
    public void start() {
        // Do nothing.
    }

    @Override
    public void stop() {
        // Do nothing.
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        applyGravity = true;
    }

    @Override
    public ConstantState getConstantState() {
        return state;
    }

    @Override
    public void draw(Canvas canvas) {
        if (applyGravity) {
            Gravity.apply(BitmapState.GRAVITY, width, height, getBounds(), destRect);
            applyGravity = false;
        }
        canvas.drawBitmap(state.bitmap, null, destRect, state.paint);
    }

    @Override
    public void setAlpha(int alpha) {
        int currentAlpha = state.paint.getAlpha();
        if (currentAlpha != alpha) {
            state.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        state.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        Bitmap bm = state.bitmap;
        // Bitmap.hasAlpha() requires API 12; assume translucency on API 1-11 to be safe.
        boolean hasAlpha;
        try {
            hasAlpha = bm == null || bm.hasAlpha();
        } catch (NoSuchMethodError e) {
            hasAlpha = true;
        }
        return hasAlpha || state.paint.getAlpha() < 255
                ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    @Override
    public Drawable mutate() {
        if (!mutated && super.mutate() == this) {
            state = new BitmapState(state);
            mutated = true;
        }
        return this;
    }

    public Bitmap getBitmap() {
        return state.bitmap;
    }

    static class BitmapState extends ConstantState {
        private static final int DEFAULT_PAINT_FLAGS = Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG;
        private static final Paint DEFAULT_PAINT = new Paint(DEFAULT_PAINT_FLAGS);
        private static final int GRAVITY = Gravity.FILL;

        final Bitmap bitmap;

        int targetDensity;
        Paint paint = DEFAULT_PAINT;

        public BitmapState(Bitmap bitmap) {
            this.bitmap = bitmap;
        }


        BitmapState(BitmapState other) {
            this(other.bitmap);
            targetDensity = other.targetDensity;
        }

        void setColorFilter(ColorFilter colorFilter) {
            mutatePaint();
            paint.setColorFilter(colorFilter);
        }

        void setAlpha(int alpha) {
            mutatePaint();
            paint.setAlpha(alpha);
        }

        // We want to create a new Paint object so we can mutate it safely.
        @SuppressWarnings("PMD.CompareObjectsWithEquals")
        void mutatePaint() {
            if (DEFAULT_PAINT == paint) {
                paint = new Paint(DEFAULT_PAINT_FLAGS);
            }
        }

        @Override
        public Drawable newDrawable() {
            return new GlideBitmapDrawable(null, this);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new GlideBitmapDrawable(res, this);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
