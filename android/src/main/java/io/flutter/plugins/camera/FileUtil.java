package io.flutter.plugins.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by tysheng
 * Date: 2019-11-27 17:25.
 * Email: tyshengsx@gmail.com
 */
public class FileUtil {
    private static final int MAX_IMAGE_WIDTH = 1920;
    private static final String TAG = "FileUtil";

    public static void copy(File src, File dst, boolean deleteSrc) throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;

        try {
            inputChannel = new FileInputStream(src).getChannel();
            outputChannel = new FileOutputStream(dst).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            if (inputChannel != null) {
                inputChannel.close();
            }

            if (outputChannel != null) {
                outputChannel.close();
            }

            if (deleteSrc) {
                src.delete();
            }
        }
    }


    public static String compress(Context context, String originalImagePath, String targetPath) {
        File originalImageFile = new File(originalImagePath);
        if (originalImageFile.exists()) {
            File compressedFile = new File(targetPath);

            OutputStream outputStream = null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(originalImagePath, options);
            int currentHeight = options.outHeight;
            int currentWidth = options.outWidth;

            Log.d(TAG, "current " + currentWidth + "," + currentHeight);
            Bitmap originalImage;

            float maxEdge = Math.max(currentHeight, currentWidth);

            if (maxEdge > MAX_IMAGE_WIDTH) {
                options.inJustDecodeBounds = false;
                options.inSampleSize = 2;

//                    while (maxEdge / options.inSampleSize > MAX_IMAGE_WIDTH) {
//                        options.inSampleSize *= 2;
//                    }

                originalImage = BitmapFactory.decodeFile(originalImagePath, options);
            } else {
                originalImage = BitmapFactory.decodeFile(originalImagePath);
            }
            originalImage = addExif(originalImage, originalImagePath);
            try {
                outputStream = context.getContentResolver().openOutputStream(Uri.fromFile(compressedFile));
                Log.d(TAG, "new " + originalImage.getWidth() + "," + originalImage.getHeight());
                originalImage.compress(Bitmap.CompressFormat.JPEG, 85, outputStream);
                originalImage.recycle();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                close(outputStream);
            }

            Log.d(TAG, "final size=" + compressedFile.length());
            return compressedFile.getAbsolutePath();
        }


        return originalImagePath;
    }

    public static void close(@Nullable Closeable c) {
        if (c != null) { // java.lang.IncompatibleClassChangeError: interface not implemented
            try {
                c.close();
            } catch (IOException e) {
                // silence
            }
        }
    }


    private static Bitmap addExif(Bitmap target, String originPath) {
        ExifInterface exif;
        Bitmap bitmap = null;
        try {
            exif = new ExifInterface(originPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
            } else if (orientation == 3) {
                matrix.postRotate(180);
            } else if (orientation == 8) {
                matrix.postRotate(270);
            }
            bitmap = Bitmap.createBitmap(target, 0, 0,
                    target.getWidth(), target.getHeight(),
                    matrix, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (bitmap == null) {
            return target;
        } else {
            return bitmap;
        }
    }
}
