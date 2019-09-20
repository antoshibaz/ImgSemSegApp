package com.imgsemseg.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.imgsemseg.R;
import com.imgsemseg.utils.ImageUtils;

import java.nio.ByteBuffer;

public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener, Camera.PreviewCallback {

    private static final String TAG = "CameraActivity";

    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private boolean debug = false;
    private boolean useCamera2API;

    protected boolean isFrontCamera = true;

    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    private Handler handler;
    private HandlerThread handlerThread;

    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void setFrontCamera(boolean frontCamera) {
        isFrontCamera = frontCamera;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main_activity);

        if (hasPermission()) {
            setCameraPreviewFragment();
        } else {
            requestPermission();
            if (hasPermission()) setCameraPreviewFragment();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    /**
     * Callback for android.hardware.Camera API
     * <p>
     * Camera API legacy for Android API < 21 or devices with not full supported
     */
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            Log.w(TAG, "Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90, isFrontCamera);
            }
        } catch (final Exception e) {
            Log.e(TAG, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };

        processImage();
    }

    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }

            isProcessingFrame = true;
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            Log.e(TAG, "ImageReader available exception");
        }
    }

    @Override
    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("BackgroundInference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Handler thread InterruptedException");
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0) {
                boolean notGrated = false;
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        notGrated = true;
                        break;
                    }
                }

                if (!notGrated)
                    setCameraPreviewFragment();
            } else {
                requestPermission();
            }
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        CameraActivity.this,
                        "Camera permission is required for this app",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    private String chooseCamera(boolean isFront) {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        int searchedCamera = (isFront) ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        try {
            String[] cams = manager.getCameraIdList();
            for (final String cameraId : cams) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing != searchedCamera) {
                    continue;
                }
                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                        (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                || isHardwareLevelSupported(
                                characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);

                return cameraId;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Not allowed to access camera");
        }

        return null;
    }

    protected void setCameraPreviewFragment() {
        String cameraId = chooseCamera(isFrontCamera);
        Fragment fragment;
        if (useCamera2API) {
            Camera2PreviewFragment camera2PreviewFragment =
                    Camera2PreviewFragment.newInstance(
                            new Camera2PreviewFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    CameraActivity.this.onPreviewSizeChosen(size, rotation, isFrontCamera);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2PreviewFragment.setCamera(cameraId);
            fragment = camera2PreviewFragment;
        } else {
            CameraPreviewFragment cameraPreviewFragment =
                    new CameraPreviewFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
            cameraPreviewFragment.setCameraId(cameraId);
            fragment = cameraPreviewFragment;
        }

        getSupportFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation, boolean isFrontCam);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();
}