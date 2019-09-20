package com.imgsemseg;

import android.app.Activity;

import com.imgsemseg.tflite.TFLiteImageSemanticSegmenter;

import java.io.IOException;

public class MobileNetDeepLabV3Float extends TFLiteImageSemanticSegmenter {

    private static final int INPUT_IMG_W = 257;
    private static final int INPUT_IMG_H = 257;
    private static final int NUM_CLASSES = 21;
    private static final int BYTES_PER_CHANNEL = 4;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;
    private static final String MODEL_FILEPATH = "deeplabv3.tflite";

    public MobileNetDeepLabV3Float(Activity activity) throws IOException {
        super(activity);
    }

    @Override
    public String getModelPath() {
        return MODEL_FILEPATH;
    }

    @Override
    public String getLabelPath() {
        return null;
    }

    @Override
    public int getImageSizeX() {
        return INPUT_IMG_W;
    }

    @Override
    public int getImageSizeY() {
        return INPUT_IMG_H;
    }

    @Override
    public int getNumBytesPerChannel() {
        return BYTES_PER_CHANNEL;
    }

    @Override
    protected void addPixelValue(int pixelValue) {
        inputImgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        inputImgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        inputImgData.putFloat((((pixelValue) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
    }

    @Override
    public int getNumLabelClasses() {
        return NUM_CLASSES;
    }

    @Override
    public String[] getClassLabels() {
        return new String[]{
                "background",
                "aeroplane",
                "bicycle",
                "bird",
                "boat",
                "bottle",
                "bus",
                "car",
                "cat",
                "chair",
                "cow",
                "diningtable",
                "dog",
                "horse",
                "motorbike",
                "person",
                "potted-plant",
                "sheep",
                "sofa",
                "train",
                "tv-monitor",
                "ambigious"
        };
    }
}