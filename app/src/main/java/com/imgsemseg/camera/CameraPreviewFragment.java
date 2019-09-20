package com.imgsemseg.camera;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.imgsemseg.R;
import com.imgsemseg.utils.ImageUtils;
import com.imgsemseg.views.AutoFitTextureView;

import java.io.IOException;
import java.util.List;

@SuppressLint("ValidFragment")
public class CameraPreviewFragment extends Fragment {

    public static final String TAG = "CameraPreviewFragment";

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Camera camera;
    private String cameraId;
    private Camera.PreviewCallback imageListener;
    private Size desiredSize;
    /**
     * The layout identifier to inflate for this Fragment.
     */
    private int layout;
    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView textureView;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
     * TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera();
//                    int index = -1;
//                    if (cameraId != null) {
//                        index = Integer.parseInt(cameraId);
//                    } else {
//                        index = getCameraId();
//                    }
//                    camera = Camera.open(index);
//
//                    try {
//                        Camera.Parameters parameters = camera.getParameters();
//                        List<String> focusModes = parameters.getSupportedFocusModes();
//                        if (focusModes != null
//                                && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//                        }
//                        List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
//                        Size[] sizes = new Size[cameraSizes.size()];
//                        int i = 0;
//                        for (Camera.Size size : cameraSizes) {
//                            sizes[i++] = new Size(size.width, size.height);
//                        }
//                        Size previewSize =
//                                Camera2PreviewFragment.chooseOptimalSize(
//                                        sizes, desiredSize.getWidth(), desiredSize.getHeight());
//                        parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
//                        camera.setDisplayOrientation(90);
//                        camera.setParameters(parameters);
//                        camera.setPreviewTexture(texture);
//                    } catch (IOException exception) {
//                        camera.release();
//                    }
//
//                    camera.setPreviewCallbackWithBuffer(imageListener);
//                    Camera.Size s = camera.getParameters().getPreviewSize();
//                    camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);
//
//                    textureView.setAspectRatio(s.height, s.width);
//                    camera.startPreview();
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;

    @SuppressLint("ValidFragment")
    public CameraPreviewFragment(
            final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
        this.imageListener = imageListener;
        this.layout = layout;
        this.desiredSize = desiredSize;
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        textureView = view.findViewById(R.id.camera_texture);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).

        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        stopCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Exception!");
        }
    }

    protected void openCamera() {
        int index = -1;
        if (cameraId != null) {
            index = Integer.parseInt(cameraId);
        } else {
            index = getCameraId();
        }
        camera = Camera.open(index);

        try {
            Camera.Parameters parameters = camera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null
                    && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
            Size[] sizes = new Size[cameraSizes.size()];
            int i = 0;
            for (Camera.Size size : cameraSizes) {
                sizes[i++] = new Size(size.width, size.height);
            }
            Size previewSize =
                    Camera2PreviewFragment.chooseOptimalSize(
                            sizes, desiredSize.getWidth(), desiredSize.getHeight());
            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
            camera.setDisplayOrientation(90);
            camera.setParameters(parameters);
            camera.setPreviewTexture(textureView.getSurfaceTexture());
        } catch (IOException exception) {
            camera.release();
        }

        camera.setPreviewCallbackWithBuffer(imageListener);
        Camera.Size s = camera.getParameters().getPreviewSize();
        camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

        textureView.setAspectRatio(s.height, s.width);
        camera.startPreview();
    }

    protected void stopCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    private int getCameraId() {
        CameraInfo ci = new CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, ci);
            if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return -1; // No camera found
    }

    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }
}