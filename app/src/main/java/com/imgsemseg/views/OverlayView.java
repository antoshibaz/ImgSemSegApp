package com.imgsemseg.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

/**
 * A simple View providing a render callback to other classes.
 */
public class OverlayView extends View {

    private final List<DrawCallback> drawCallbacks = new LinkedList<DrawCallback>();

    public OverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public void addDrawCallback(final DrawCallback callback) {
        drawCallbacks.add(callback);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public synchronized void draw(final Canvas canvas) {
        for (final DrawCallback callback : drawCallbacks) {
            callback.drawCallback(canvas);
        }
    }

    /**
     * Interface defining the callback for client classes.
     */
    public interface DrawCallback {
        void drawCallback(final Canvas canvas);
    }
}