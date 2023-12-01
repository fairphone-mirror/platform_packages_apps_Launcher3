package com.android.launcher3.secondarydisplay.wallpaper.tileinfo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.secondarydisplay.wallpaper.common.Utils;
import com.android.launcher3.R;
import com.android.launcher3.secondarydisplay.wallpaper.SecondaryWallpaperPicker;
import com.android.launcher3.secondarydisplay.wallpaper.common.InputStreamProvider;

public abstract class WallpaperTileInfo {

    protected View mView;

    public void onClick(SecondaryWallpaperPicker a) {}

    public void onSave(SecondaryWallpaperPicker a) {}

    public void onDelete(SecondaryWallpaperPicker a) {}

    public boolean isSelectable() { return false; }

    public boolean isNamelessWallpaper() { return false; }

    public void onIndexUpdated(CharSequence label) {
        if (isNamelessWallpaper()) {
            mView.setContentDescription(label);
        }
    }

    public abstract View createView(Context context, LayoutInflater inflator, ViewGroup parent);

    protected static Point getDefaultThumbSize(Resources res) {
        return new Point(res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth),
                res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight));

    }

    protected static Bitmap createThumbnail(InputStreamProvider streamProvider, Context context,
            int rotation, boolean leftAligned) {
        Point size = getDefaultThumbSize(context.getResources());
        int width = size.x;
        int height = size.y;
        Point bounds = streamProvider.getImageBounds();
        if (bounds == null) {
            return null;
        }

        Matrix rotateMatrix = new Matrix();
        rotateMatrix.setRotate(rotation);
        float[] rotatedBounds = new float[] { bounds.x, bounds.y };
        rotateMatrix.mapPoints(rotatedBounds);
        rotatedBounds[0] = Math.abs(rotatedBounds[0]);
        rotatedBounds[1] = Math.abs(rotatedBounds[1]);

        RectF cropRect = Utils.getMaxCropRect(
                (int) rotatedBounds[0], (int) rotatedBounds[1], width, height, leftAligned);
        return streamProvider.readCroppedBitmap(cropRect, width, height, rotation);
    }
}