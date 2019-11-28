package io.flutter.plugins.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugins.camera.common.CameraSource;
import io.flutter.plugins.camera.common.CameraSourcePreview;
import io.flutter.view.TextureRegistry;
import io.flutter.view.FlutterView;

final class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {
    private final Activity activity;
    private BinaryMessenger messenger;
    private MethodChannel methodChannel;
    private @Nullable
    QrReader camera;
    private static CameraManager cameraManager;
    private TextureRegistry textureRegistry;
    private DartMessenger dartMessenger;
    private static final String TAG = "MethodCallHandlerImpl";

    MethodCallHandlerImpl(
            Activity activity,
            BinaryMessenger messenger,
            TextureRegistry textureRegistry) {
        this.activity = activity;
        this.messenger = messenger;
        this.textureRegistry = textureRegistry;

        methodChannel = new MethodChannel(messenger, "plugins.flutter.io/camera");
        methodChannel.setMethodCallHandler(this);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity == MethodCallHandlerImpl.this.activity) {
                    if (camera != null) {
                        camera.startCameraSource();
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (activity == MethodCallHandlerImpl.this.activity) {
                    if (camera != null) {
                        if (camera.preview != null) {
                            camera.preview.stop();

                        }
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (activity == MethodCallHandlerImpl.this.activity) {
                    if (camera != null) {
                        if (camera.preview != null) {
                            camera.preview.stop();
                        }

                        if (camera.cameraSource != null) {
                            camera.cameraSource.release();
                        }
                    }
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        switch (call.method) {
            case "availableCameras":
                try {
                    result.success(CameraUtils.getAvailableCameras(activity));
                } catch (Exception e) {
                    handleException(e, result);
                }
                break;
            case "initialize": {
                String cameraName = call.argument("cameraName");
                String resolutionPreset = call.argument("resolutionPreset");

                if (camera != null) {
                    camera.close();
                }
                camera = new QrReader(cameraName, resolutionPreset, result);
//                result.success(null);
                break;
            }
            case "takePicture": {
                String path = call.argument("path");
                if (camera != null) {
                    camera.takePhoto(path, result);
                }
                break;
            }
            case "dispose": {
                if (camera != null) {
                    camera.dispose();
                }
                result.success(null);
                break;
            }
            default:
                Log.d(TAG, "onMethodCall: default " + call.method);
                result.success(null);
                break;
        }
    }

    void stopListening() {
        methodChannel.setMethodCallHandler(null);
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    @SuppressWarnings("ConstantConditions")
    private void handleException(Exception exception, Result result) {
        if (exception instanceof CameraAccessException) {
            result.error("CameraAccess", exception.getMessage(), null);
        }

        throw (RuntimeException) exception;
    }


    private class QrReader {

        private CameraSource cameraSource = null;
        private CameraSourcePreview preview;
        private static final String TAG = "QrReader";
        private final FlutterView.SurfaceTextureEntry textureEntry;

        private boolean isFrontFacing;

        private void startCameraSource() {
            if (cameraSource != null) {
                try {
                    if (preview == null) {
                        Log.d(TAG, "resume: Preview is null");
                    } else {
                        preview.start(cameraSource);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source.", e);
                    cameraSource.release();
                    cameraSource = null;
                }
            }
        }

        //
        QrReader(final String cameraName, final String resolutionPreset, @NonNull final Result result) {

            textureEntry = textureRegistry.createSurfaceTexture();

            try {

                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);

                isFrontFacing =
                        characteristics.get(CameraCharacteristics.LENS_FACING)
                                == CameraMetadata.LENS_FACING_FRONT;

                open(result);
            } catch (CameraAccessException e) {
                result.error("CameraAccess", e.getMessage(), null);
            } catch (IllegalArgumentException e) {
                result.error("IllegalArgumentException", e.getMessage(), null);
            }
        }

        //
        private void registerEventChannel() {
            dartMessenger = new DartMessenger(messenger, textureEntry.id());
        }

        //
        private boolean hasCameraPermission() {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || activity.checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }

        //
//
        @SuppressLint("MissingPermission")
        private void open(@Nullable final Result result) {
            if (!hasCameraPermission()) {
                if (result != null) {
                    result.error("cameraPermission", "Camera permission not granted", null);
                }
            } else {
                cameraSource = new CameraSource(activity);
                cameraSource.setFacing(isFrontFacing ? 1 : 0);

                preview = new CameraSourcePreview(activity, null, textureEntry.surfaceTexture());

                startCameraSource();
//                registerEventChannel();

                Map<String, Object> reply = new HashMap<>();
                reply.put("textureId", textureEntry.id());
                reply.put("previewWidth", cameraSource.getPreviewSize().getWidth());
                reply.put("previewHeight", cameraSource.getPreviewSize().getHeight());
                result.success(reply);

            }
        }


        private void close() {
            if (preview != null) {
                preview.stop();
            }

            if (cameraSource != null) {
                cameraSource.release();
            }

            camera = null;

        }

        private void takePhoto(String path, Result result) {
            if (cameraSource != null) {
                cameraSource.takePhoto(new android.hardware.Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
                        long start = System.currentTimeMillis();
                        File temp = new File(activity.getCacheDir(), "photo_temp.jpg");
                        if (!temp.exists()) {
                            try {
                                temp.createNewFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        try {
                            FileOutputStream fos = new FileOutputStream(temp);
                            fos.write(data);
                            fos.close();
                        } catch (Exception e) {
                            Log.e(TAG, "onPictureTaken: ", e);
                        }
                        Log.d(TAG, "onPictureTaken: " + path);
                        long timeCost = System.currentTimeMillis() - start;
                        Log.d(TAG, "onPictureTaken: " + timeCost);
                        FileUtil.compress(activity, temp.getAbsolutePath(), new File(path).getAbsolutePath());
                        result.success(path);
                    }
                });
            }
        }

        private void dispose() {
            textureEntry.release();
            if (preview != null) {
                preview.stop();
            }

            if (cameraSource != null) {
                cameraSource.release();
            }
        }
    }
}
