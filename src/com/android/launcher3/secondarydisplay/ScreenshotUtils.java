package com.android.launcher3.secondarydisplay;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import android.app.Activity;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.util.DisplayMetrics;
import android.view.WindowManagerGlobal;
import android.window.ScreenCapture;
import android.window.ScreenCapture.CaptureArgs;
import android.window.ScreenCapture.ScreenshotHardwareBuffer;
import android.window.ScreenCapture.SynchronousScreenCaptureListener;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import android.view.Display;
import android.view.DisplayAddress;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import android.os.RemoteException;
import com.android.launcher3.R;

public class ScreenshotUtils {
    private static final String TAG = "ScreenshotUtils";

    private static final String SCREENSHOTS_PATH = Environment.DIRECTORY_PICTURES
            + File.separator + Environment.DIRECTORY_SCREENSHOTS;
    static final Duration PENDING_ENTRY_TTL = Duration.ofHours(24);
    private static final String FILENAME_PATTERN = "Secondary_Screenshot_%1$tY%<tm%<td-%<tH%<tM%<tS.%2$s";
    private static Uri mImageUri;

    private static String createFilename(ZonedDateTime time, Bitmap.CompressFormat format) {
        return String.format(FILENAME_PATTERN, time, fileExtension(format));
    }

    static String fileExtension(Bitmap.CompressFormat format) {
        switch (format) {
            case JPEG:
                return "jpg";
            case PNG:
                return "png";
            case WEBP:
            case WEBP_LOSSY:
            case WEBP_LOSSLESS:
                return "webp";
            default:
                throw new IllegalArgumentException("Unknown CompressFormat!");
        }
    }

    static String getMimeType(Bitmap.CompressFormat format) {
        switch (format) {
            case JPEG:
                return "image/jpeg";
            case PNG:
                return "image/png";
            case WEBP:
            case WEBP_LOSSLESS:
            case WEBP_LOSSY:
                return "image/webp";
            default:
                throw new IllegalArgumentException("Unknown CompressFormat!");
        }
    }

    private static ContentValues createMetadata(ZonedDateTime captureTime, Bitmap.CompressFormat format,
                                                String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, SCREENSHOTS_PATH);
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(format));
        values.put(MediaStore.MediaColumns.DATE_ADDED, captureTime.toEpochSecond());
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, captureTime.toEpochSecond());
        values.put(MediaStore.MediaColumns.DATE_EXPIRES,
                captureTime.plus(PENDING_ENTRY_TTL).toEpochSecond());
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        return values;
    }

    public static Uri getImageUri(){
        return mImageUri;
    }

    private static void publishEntry(ContentResolver resolver, Uri uri) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        values.putNull(MediaStore.MediaColumns.DATE_EXPIRES);
        resolver.update(uri, values, null);
    }

    private static void savePic(Context context,Bitmap bitmap,  Bitmap.CompressFormat format,Consumer<Uri> callback) {
        ZonedDateTime time = ZonedDateTime.now();
        String fileName = createFilename(time, format);
        final ContentValues values = createMetadata(time, format, fileName);
        mImageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        UI_HELPER_EXECUTOR.execute(()->{
            FileOutputStream out = null;
            try {
                out = (FileOutputStream) context.getContentResolver().openOutputStream(mImageUri);
                if (null != out) {
                    bitmap.compress(format, 100, out);
                    out.flush();
                    out.close();
                }
                publishEntry(context.getContentResolver(),mImageUri);
                MAIN_EXECUTOR.execute(()->{
                    callback.accept(mImageUri);
                });
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void takeScreenShot(Activity activity,Consumer<Uri> callback){
        Resources resources = activity.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Bitmap bitmap = captureScreenshot(activity,new Rect(0,0,dm.widthPixels, dm.heightPixels));
        savePic(activity,bitmap, Bitmap.CompressFormat.PNG,callback);
    }

    private static Bitmap captureScreenshot(Context context,Rect crop) {
        Bitmap screenshot = null;
        final Display display = context.getDisplay();
        final DisplayAddress address = display.getAddress();
        if (!(address instanceof DisplayAddress.Physical)) {
            Log.e(TAG, "Skipping Screenshot - Default display does not have a physical address: "
                    + display);
        } else {
            final DisplayAddress.Physical physicalAddress = (DisplayAddress.Physical) address;

            final CaptureArgs captureArgs = new CaptureArgs.Builder<>()
                    .setSourceCrop(crop)
                    .build();
            SynchronousScreenCaptureListener syncScreenCapture =
                    ScreenCapture.createSyncCaptureListener();
            try {
                WindowManagerGlobal.getWindowManagerService().captureDisplay(display.getDisplayId(),
                        captureArgs, syncScreenCapture);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to request screencapture for display");
                return null;
            }
            final ScreenshotHardwareBuffer screenshotHardwareBuffer =
                    syncScreenCapture.getBuffer();
            screenshot = screenshotHardwareBuffer == null ? null : screenshotHardwareBuffer.asBitmap();
        }
        return screenshot;
    }
}
