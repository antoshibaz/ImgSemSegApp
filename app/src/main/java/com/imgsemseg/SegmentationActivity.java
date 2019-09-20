package com.imgsemseg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.imgsemseg.camera.CameraActivity;
import com.imgsemseg.tflite.TFLiteGpuDelegateHelper;
import com.imgsemseg.tflite.TFLiteImageSemanticSegmenter;
import com.imgsemseg.utils.ImageUtils;
import com.imgsemseg.views.OverlayView;

public class SegmentationActivity extends CameraActivity {

    private static final String TAG = "SegmentationActivity";

    private TFLiteImageSemanticSegmenter imageSegmenter;
    private OverlayView segmentationOverlay;
    private int[] coloredMaskClasses;

    private Bitmap rgbCameraFrameBitmap = null;
    private Bitmap segmentedFrameBitmap = null;

    private Matrix transformMat;

    private String[] classes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setFrontCamera(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation, boolean isFrontCam) {
        try {
            imageSegmenter = new MobileNetDeepLabV3Float(this);
            imageSegmenter.setNumThreads(4);
            if (TFLiteGpuDelegateHelper.isGpuDelegateAvailable()) {
                imageSegmenter.useGpu();
            } else {
                imageSegmenter.useCPU();
            }
            //imageSegmenter.useNNAPI();
            classes = imageSegmenter.getClassLabels();
        } catch (Exception e) {
            Log.e(TAG, "Failed to init image segmenter");
            Toast.makeText(this, "Failed to init image segmenter", Toast.LENGTH_LONG).show();
            finish();
        }

        segmentationOverlay = findViewById(R.id.segmentation_overlay);
        coloredMaskClasses = ImageUtils.getRandomColorsForClasses(imageSegmenter.getNumLabelClasses(), 200);
        coloredMaskClasses[0] = Color.TRANSPARENT;
        rgbCameraFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);

        segmentationOverlay.addDrawCallback(new OverlayView.DrawCallback() {
            @Override
            public void drawCallback(Canvas canvas) {
                float ratio = previewHeight / (float) previewWidth;
                int w = canvas.getWidth();
                int h = canvas.getHeight();
                if (w < h * ratio) {
                    h = (int) (w * (1 / ratio));
                } else {
                    w = (int) (h * ratio);
                }

                if (segmentedFrameBitmap != null) {
                    Bitmap bitmap = Bitmap.createScaledBitmap(segmentedFrameBitmap, w, h, true);
                    canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
                }
            }
        });

        transformMat = new Matrix();
        transformMat.postRotate(-rotation);
        if (isFrontCam) {
            transformMat.preScale(1, -1);
        } else {
            transformMat.preScale(-1, -1);
        }
    }

    @Override
    protected void processImage() {
        if (imageSegmenter == null) {
            return;
        }

        rgbCameraFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Bitmap rotCamFrame = Bitmap.createBitmap(rgbCameraFrameBitmap, 0, 0, previewWidth, previewHeight,
                transformMat, true);

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        long startTime = SystemClock.uptimeMillis();
                        segmentedFrameBitmap = imageSegmenter.predictSegmentation(rotCamFrame, coloredMaskClasses);
                        long endTime = SystemClock.uptimeMillis();
                        Log.i(TAG, "Segmentation inference time(ms): " + String.valueOf(endTime - startTime));
                        StringBuilder sbDetCl = new StringBuilder();
                        for (Integer cl : imageSegmenter.getHitClassIdxArray()) {
                            sbDetCl.append(classes[cl] + " ");
                        }
                        Log.i(TAG, "Detected classes: " + sbDetCl);

                        segmentationOverlay.postInvalidate();
                        readyForNextImage();
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_preview_fragment;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return new Size(1920, 1080);
    }
}