package com.android.launcher3.secondarydisplay.wallpaper.tileinfo;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.BitmapFactory;

import com.android.launcher3.R;
import com.android.launcher3.secondarydisplay.wallpaper.common.Utils;
import com.android.launcher3.secondarydisplay.wallpaper.SecondaryWallpaperPicker;
import com.android.launcher3.secondarydisplay.wallpaper.common.InputStreamProvider;

public class UriWallpaperInfo extends DrawableThumbWallpaperInfo {

    private static final String TAG = "UriWallpaperInfo";

    public final Uri mUri;

    public UriWallpaperInfo(Uri uri) {
        super(null);
        mUri = uri;
    }

    @Override
    public void onClick(final SecondaryWallpaperPicker a) {
        loadThumbnaleAsync(a);
        loadImage(a ,false);
    }

    @Override
    public void onSave(final SecondaryWallpaperPicker a) {
        loadImage(a, true);
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public boolean isNamelessWallpaper() {
        return true;
    }

    public void loadThumbnaleAsync(final SecondaryWallpaperPicker activity) {
        mView.setVisibility(View.GONE);
        new AsyncTask<Void, Void, Bitmap>() {
            protected Bitmap doInBackground(Void...args) {
                try {
                    InputStreamProvider isp = InputStreamProvider.fromUri(activity, mUri);
                    int rotation = isp.getRotationFromExif(activity);
                    return createThumbnail(isp, activity, rotation, false);
                } catch (SecurityException securityException) {
                    if (activity.isActivityDestroyed()) {
                        // Temporarily granted permissions are revoked when the activity
                        // finishes, potentially resulting in a SecurityException here.
                        // Even though {@link #isDestroyed} might also return true in different
                        // situations where the configuration changes, we are fine with
                        // catching these cases here as well.
                        cancel(false);
                    } else {
                        // otherwise it had a different cause and we throw it further
                        throw securityException;
                    }
                    return null;
                }
            }
            protected void onPostExecute(Bitmap thumb) {
                if (!isCancelled() && thumb != null) {
                    BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), thumb);
                    setThumb(drawable);
                    mView.setVisibility(View.VISIBLE);
                } else {
                    Log.e(TAG, "Error loading thumbnail for uri=" + mUri);
                }
            }
        }.execute();
    }

    private void loadImage(final SecondaryWallpaperPicker activity,boolean saveImage){
        int height = activity.getDisplay().getHeight();
        int width = activity.getDisplay().getWidth();
        UI_HELPER_EXECUTOR.execute(()->{
            try {
                InputStreamProvider isp = InputStreamProvider.fromUri(activity, mUri);
                int rotation = isp.getRotationFromExif(activity);
                // Point size = new Point(width,height);
                // int width = size.x;
                // int height = size.y;
                Point bounds = isp.getImageBounds();
                if (bounds == null) {
                    return;
                }
        
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setRotate(rotation);
                float[] rotatedBounds = new float[] { bounds.x, bounds.y };
                rotateMatrix.mapPoints(rotatedBounds);
                rotatedBounds[0] = Math.abs(rotatedBounds[0]);
                rotatedBounds[1] = Math.abs(rotatedBounds[1]);
        
                RectF cropRect = Utils.getMaxCropRect(
                        (int) rotatedBounds[0], (int) rotatedBounds[1], width, height, true);
                Bitmap bitmap = isp.readCroppedBitmap(cropRect, width, height, rotation);
                if(bitmap != null){
                    if(saveImage){
                        activity.saveImage(bitmap);
                    } else {
                        MAIN_EXECUTOR.execute(()->{
                            BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
                            activity.updatePreview(drawable);
                        });
                    }
                }
            } catch (SecurityException securityException) {
            }
        });
    }
}