package com.android.launcher3.secondarydisplay.wallpaper;

import static android.content.Context.MODE_PRIVATE;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.Button;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.R;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.secondarydisplay.wallpaper.tileinfo.WallpaperTileInfo;
import com.android.launcher3.secondarydisplay.wallpaper.tileinfo.PickImageInfo;
import com.android.launcher3.secondarydisplay.wallpaper.tileinfo.UriWallpaperInfo;
import com.android.launcher3.secondarydisplay.wallpaper.tileinfo.ResourceWallpaperInfo;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SecondaryWallpaperPicker extends Activity implements View.OnClickListener{
    private final String TAG = "SecondaryWallpaperPicker";
    public static final int IMAGE_PICK = 5;
    public static final String SECONDARY_WALLPAPER = "secondary_wallpaper";
    private LinearLayout mWallpapersView;
    private ImageView mIvPreView;
    private View mSelectedTile;
    private Button mSetWallpaper;
    private int mSelectedIndex = -1;
    ArrayList<Uri> mTempWallpaperTiles = new ArrayList<Uri>();
    private SharedPreferences mSharedPreferences;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.secondary_wallpaper_picker);
        mWallpapersView = findViewById(R.id.wallpaper_list);
        mIvPreView = findViewById(R.id.iv_preview);
        mSetWallpaper = findViewById(R.id.set_wallpaper);
        mSharedPreferences = LauncherPrefs.getPrefs(this);
        mSetWallpaper.setOnClickListener(view->{
            if(mSelectedTile != null){
                WallpaperTileInfo info = (WallpaperTileInfo) mSelectedTile.getTag();
                if(info != null){
                    info.onSave(SecondaryWallpaperPicker.this);
                }
                mHandler.postDelayed(()->{
                    SecondaryWallpaperPicker.this.finish();
                },600);
            }
        });
        String path = mSharedPreferences.getString(SECONDARY_WALLPAPER, null);
        if(path != null && path.endsWith("png")) {
            File file = new File(path);
            if(file.exists()){
                ArrayList<WallpaperTileInfo> savedImage = new ArrayList<>();
                UriWallpaperInfo info = new UriWallpaperInfo(Uri.fromFile(file));
                savedImage.add(info);
                populateWallpapers(mWallpapersView, savedImage);
            }
        }
        ArrayList<WallpaperTileInfo> wallpapers = findBundledWallpapers();
        populateWallpapers(mWallpapersView, wallpapers);
        View first = mWallpapersView.getChildAt(0);
        onClick(first);

        LinearLayout masterWallpaperList = (LinearLayout) findViewById(R.id.master_wallpaper_list);
        masterWallpaperList.addView(
                createTileView(masterWallpaperList, new PickImageInfo()), 0);
    }

    public ArrayList<WallpaperTileInfo> findBundledWallpapers() {
        final ArrayList<WallpaperTileInfo> bundled = new ArrayList<WallpaperTileInfo>(24);
        Pair<ApplicationInfo, Integer> r = getWallpaperArrayResourceId();
        if (r != null) {
            try {
                Resources wallpaperRes = getPackageManager().getResourcesForApplication(r.first);
                addWallpapers(bundled,wallpaperRes, r.first.packageName, r.second);
            } catch (PackageManager.NameNotFoundException e) {
            }
        }
        return bundled;
    }

    public Pair<ApplicationInfo, Integer> getWallpaperArrayResourceId() {
        return new Pair<>(getApplicationInfo(), R.array.wallpapers);
    }

    public void addWallpapers(ArrayList<WallpaperTileInfo> known, Resources res,
            String packageName, int listResId) {
        final String[] extras = res.getStringArray(listResId);
        for (String extra : extras) {
            int resId = res.getIdentifier(extra, "drawable", packageName);
            if (resId != 0) {
                final int thumbRes = res.getIdentifier(extra + "_small", "drawable", packageName);

                if (thumbRes != 0) {
                    ResourceWallpaperInfo wallpaperInfo =
                            new ResourceWallpaperInfo(res, resId, res.getDrawable(thumbRes));
                    known.add(wallpaperInfo);
                    Log.d(TAG, "add: [" + packageName + "]: " + extra + " (" + res + ")");
                }
            } else {
                Log.e(TAG, "Couldn't find wallpaper " + extra);
            }
        }
    }

    private void populateWallpapers(ViewGroup parent, List<? extends WallpaperTileInfo> wallpapers) {
        for (WallpaperTileInfo info : wallpapers) {
            parent.addView(createTileView(parent, info));
        }
    }

    private View createTileView(ViewGroup parent, WallpaperTileInfo info) {
        View view = info.createView(this, getLayoutInflater(), parent);
        view.setTag(info);
        view.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        WallpaperTileInfo info = (WallpaperTileInfo) v.getTag();
        if (info.isSelectable() && v.getVisibility() == View.VISIBLE) {
            selectTile(v);
        }
        info.onClick(this);
    }

    public void selectTile(View v) {
        if (mSelectedTile != null) {
            mSelectedTile.setSelected(false);
            mSelectedTile = null;
        }
        mSelectedTile = v;
        v.setSelected(true);
        mSelectedIndex = mWallpapersView.indexOfChild(v);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                addTemporaryWallpaperTile(uri);
            }
        } 
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void addTemporaryWallpaperTile(final Uri uri) {

        // Add a tile for the image picked from Gallery, reusing the existing tile if there is one.
        View imageTile = null;
        int indexOfExistingTile = 0;
        for (; indexOfExistingTile < mWallpapersView.getChildCount(); indexOfExistingTile++) {
            View thumbnail = mWallpapersView.getChildAt(indexOfExistingTile);
            Object tag = thumbnail.getTag();
            if (tag instanceof UriWallpaperInfo && ((UriWallpaperInfo) tag).mUri.equals(uri)) {
                imageTile = thumbnail;
                break;
            }
        }
        final UriWallpaperInfo info;
        if (imageTile != null) {
            // Always move the existing wallpaper to the front so user can see it without scrolling.
            mWallpapersView.removeViewAt(indexOfExistingTile);
            info = (UriWallpaperInfo) imageTile.getTag();
        } else {
            // This is the first time this temporary wallpaper has been added
            info = new UriWallpaperInfo(uri);
            imageTile = createTileView(mWallpapersView, info);
            mTempWallpaperTiles.add(uri);
        }
        mWallpapersView.addView(imageTile, 0);
        //info.loadThumbnaleAsync(this);

        onClick(imageTile);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public boolean isActivityDestroyed() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed();
    }

    public void updatePreview(Drawable drawable){
        mIvPreView.setImageDrawable(drawable);
    }

    public void savePath(String path) {
        if(path != null) {
            mSharedPreferences.edit().putString(SECONDARY_WALLPAPER, path).apply();
        }
    }

    public void saveImage(Bitmap bitmap){
        UI_HELPER_EXECUTOR.execute(()->{
            File imageDir = new File(this.getFilesDir(),"secondary_wallpaper");
            imageDir.mkdirs();
            File imageFile = new File(imageDir,"desktop_wallpaper.png");
            if(imageFile.exists()){
                imageFile.delete();
            }
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                MAIN_EXECUTOR.execute(()->{
                    String path = imageFile.getAbsolutePath();
                    savePath(path);
                });
            } catch (IOException e) {
                Log.e(TAG, "Error saving image", e);
            }
        });
    }
}
