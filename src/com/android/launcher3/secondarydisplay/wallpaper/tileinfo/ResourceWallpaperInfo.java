package com.android.launcher3.secondarydisplay.wallpaper.tileinfo;

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.android.launcher3.secondarydisplay.wallpaper.SecondaryWallpaperPicker;


public class ResourceWallpaperInfo extends DrawableThumbWallpaperInfo {

    private final Resources mResources;
    private final int mResId;

    public ResourceWallpaperInfo(Resources res, int resId, Drawable thumb) {
        super(thumb);
        mResources = res;
        mResId = resId;
    }

    @Override
    public void onClick(final SecondaryWallpaperPicker a) {
       Drawable drawable = a.getResources().getDrawable(mResId);
       a.updatePreview(drawable);
    }

    @Override
    public void onSave(SecondaryWallpaperPicker a) {
       a.savePath(String.valueOf(mResId));
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isNamelessWallpaper() {
        return true;
    }
}