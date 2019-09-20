package com.imgsemseg.tflite;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class TFLiteImageSemanticSegmenter {

    private static final String TAG = "TFLiteImageSemanticSegmenter";

    /**
     * Dimensions of image inputs.
     */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;

    /**
     * Preallocated buffers for storing image data.
     */
    private int[] intValuesTempBuff = new int[getImageSizeX() * getImageSizeY()];

    /**
     * Options for configuring the Interpreter.
     */
    private final Interpreter.Options tfLiteOptions = new Interpreter.Options();

    /**
     * The loaded TensorFlow Lite model.
     */
    private MappedByteBuffer tfLiteModel;

    /**
     * An instance of the driver class to run model inference with TensorFlow Lite.
     */
    protected Interpreter tfLiteInterpreter;

    /**
     * Labels corresponding to the output of the vision model.
     */
    private List<String> labelList;

    /**
     * A ByteBuffer to hold image data, to be feed into TensorFlow Lite as inputs.
     */
    protected ByteBuffer inputImgData;

    /**
     * A ByteBuffer to hold output image data after run inference model.
     */
    protected ByteBuffer outputImgPredictedMasks;

    private boolean[] hitClsVector = new boolean[this.getNumLabelClasses()];
    private ArrayList<Integer> hitClsIdx = new ArrayList<>();

    /**
     * Holds a gpu delegate
     */
    private Delegate gpuDelegate = null;

    public TFLiteImageSemanticSegmenter(Activity activity) throws IOException {
        tfLiteModel = loadModelFile(activity);
        labelList = loadLabelList(activity);
        tfLiteInterpreter = new Interpreter(tfLiteModel, tfLiteOptions);

        inputImgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE
                                * getImageSizeX()
                                * getImageSizeY()
                                * DIM_PIXEL_SIZE
                                * getNumBytesPerChannel());
        inputImgData.order(ByteOrder.nativeOrder());

        outputImgPredictedMasks = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE
                        * getImageSizeX()
                        * getImageSizeY()
                        * getNumBytesPerChannel()
                        * getNumLabelClasses());
        outputImgPredictedMasks.order(ByteOrder.nativeOrder());

        Log.i(TAG, "Input tensor count: " + Integer.toString(tfLiteInterpreter.getInputTensorCount()));
        Log.i(TAG, "Output tensor count: " + Integer.toString(tfLiteInterpreter.getOutputTensorCount()));
        Log.i(TAG, "Input tensor shape: " + Arrays.toString(tfLiteInterpreter.getInputTensor(0).shape()));
        Log.i(TAG, "Output tensor shape: " + Arrays.toString(tfLiteInterpreter.getOutputTensor(0).shape()));
    }

    /**
     * Segment input frame.
     *
     * @return Predicted colored mask classes on bitmap image
     */
    public Bitmap predictSegmentation(Bitmap inputImage, int[] classColors) {
        if (tfLiteInterpreter == null) {
            return null;
        }

        Bitmap resizedToModelInputImg = Bitmap.createScaledBitmap(inputImage,
                getImageSizeX(), getImageSizeY(), true);
        convertBitmapToByteBuffer(resizedToModelInputImg);

        hitClsIdx.clear();
        Arrays.fill(hitClsVector, false);
        outputImgPredictedMasks.rewind();
        tfLiteInterpreter.run(inputImgData, outputImgPredictedMasks);

        Bitmap clMasksBitmap = Bitmap.createBitmap(getImageSizeX(), getImageSizeY(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < getImageSizeY(); y++) {
            for (int x = 0; x < getImageSizeX(); x++) {
                float maxPredictionVal = -0xFF;
                int cl = -1;
                for (int c = 0; c < getNumLabelClasses(); c++) {
                    float predictionVal = outputImgPredictedMasks.getFloat(
                            (y * getImageSizeX() * getNumLabelClasses() +
                                    x * getNumLabelClasses() + c) * getNumBytesPerChannel());

                    if (c == 0 || predictionVal > maxPredictionVal) {
                        maxPredictionVal = predictionVal;
                        cl = c;
                    }
                }
                if (!hitClsVector[cl]) {
                    hitClsVector[cl] = true;
                    hitClsIdx.add(cl);
                }
                intValuesTempBuff[y * getImageSizeX() + x] = classColors[cl];
            }
        }

        clMasksBitmap.setPixels(intValuesTempBuff, 0, clMasksBitmap.getWidth(), 0, 0,
                clMasksBitmap.getWidth(), clMasksBitmap.getHeight());
        clMasksBitmap = Bitmap.createScaledBitmap(clMasksBitmap, inputImage.getWidth(), inputImage.getHeight(), false);

        return clMasksBitmap;
    }

    public boolean[] getHitClassVector() {
        return hitClsVector;
    }

    public List<Integer> getHitClassIdxArray() {
        return hitClsIdx;
    }

    /**
     * Segment input frame.
     *
     * @return Predicted mask classes in form int array
     */
    public int[] predictSegmentation(Bitmap inputImage) {
        if (tfLiteInterpreter == null) {
            return null;
        }

        Bitmap resizedToModelInputImg = Bitmap.createScaledBitmap(inputImage,
                getImageSizeX(), getImageSizeY(), true);
        convertBitmapToByteBuffer(resizedToModelInputImg);

        outputImgPredictedMasks.rewind();
        tfLiteInterpreter.run(inputImgData, outputImgPredictedMasks);

        Bitmap clMasksBitmap = Bitmap.createBitmap(getImageSizeX(), getImageSizeY(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < getImageSizeY(); y++) {
            for (int x = 0; x < getImageSizeX(); x++) {
                float maxPredictionVal = -0xFF;
                int cl = -1;
                for (int c = 0; c < getNumLabelClasses(); c++) {
                    float predictionVal = outputImgPredictedMasks.getFloat(
                            (y * getImageSizeX() * getNumLabelClasses() +
                                    x * getNumLabelClasses() + c) * getNumBytesPerChannel());

                    if (c == 0 || predictionVal > maxPredictionVal) {
                        maxPredictionVal = predictionVal;
                        cl = c;
                    }
                }
                intValuesTempBuff[y * getImageSizeX() + x] = (cl & 0xFF) << 24;
            }
        }

        clMasksBitmap.setPixels(intValuesTempBuff, 0, clMasksBitmap.getWidth(), 0, 0,
                clMasksBitmap.getWidth(), clMasksBitmap.getHeight());
        clMasksBitmap = Bitmap.createScaledBitmap(clMasksBitmap, inputImage.getWidth(), inputImage.getHeight(), false);

        int[] resultClMasks = new int[inputImage.getWidth() * inputImage.getHeight()];
        clMasksBitmap.getPixels(resultClMasks, 0, inputImage.getWidth(), 0, 0,
                inputImage.getWidth(), inputImage.getHeight());

        for (int i = 0; i < resultClMasks.length; i++) {
            resultClMasks[i] = (resultClMasks[i] >> 24) & 0xFF;
        }

        return resultClMasks;
    }

    private void recreateInterpreter() {
        if (tfLiteInterpreter != null) {
            tfLiteInterpreter.close();
            // gpuDelegate.close() ???
            tfLiteInterpreter = new Interpreter(tfLiteModel, tfLiteOptions);
        }
    }

    public void useGpu() {
        if (gpuDelegate == null && TFLiteGpuDelegateHelper.isGpuDelegateAvailable()) {
            gpuDelegate = TFLiteGpuDelegateHelper.createGpuDelegate();
            tfLiteOptions.addDelegate(gpuDelegate);
            recreateInterpreter();
        }
    }

    public void useCPU() {
        tfLiteOptions.setUseNNAPI(false);
        recreateInterpreter();
    }

    public void useNNAPI() {
        tfLiteOptions.setUseNNAPI(true);
        recreateInterpreter();
    }

    public void setNumThreads(int numThreads) {
        tfLiteOptions.setNumThreads(numThreads);
        recreateInterpreter();
    }

    /**
     * Closes tflite to release resources.
     */
    public void close() {
        tfLiteInterpreter.close();
        tfLiteInterpreter = null;
        tfLiteModel = null;
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Activity activity) throws IOException {
        String labelPath = getLabelPath();
        if (labelPath == null) {
            return null;
        }

        List<String> labelList = new ArrayList<>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(getLabelPath())));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();

        return labelList;
    }

    /**
     * Memory-map the model file in Assets.
     */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (inputImgData == null) {
            return;
        }

        inputImgData.rewind();
        bitmap.getPixels(intValuesTempBuff, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int y = 0; y < getImageSizeY(); y++) {
            for (int x = 0; x < getImageSizeX(); x++) {
                final int val = intValuesTempBuff[y * getImageSizeX() + x];
                addPixelValue(val);
            }
        }
    }

    /**
     * Get the name of the model file stored in Assets.
     */
    public abstract String getModelPath();

    /**
     * Get the name of the label file stored in Assets.
     */
    public abstract String getLabelPath();

    /**
     * Get the image size along the x axis.
     */
    public abstract int getImageSizeX();

    /**
     * Get the image size along the y axis.
     */
    public abstract int getImageSizeY();

    /**
     * Get the number of bytes that is used to store a single color channel value.
     */
    public abstract int getNumBytesPerChannel();

    /**
     * Add pixelValue to byteBuffer.
     */
    protected abstract void addPixelValue(int pixelValue);

    /**
     * Get the total number of label classes.
     */
    public abstract int getNumLabelClasses();

    public abstract String[] getClassLabels();
}