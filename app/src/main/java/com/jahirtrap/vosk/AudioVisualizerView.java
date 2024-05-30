package com.jahirtrap.vosk;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class AudioVisualizerView extends View {
    private static final int LINE_WIDTH = 5;
    private static final int LINE_SPACE = 5;
    private static final float SCALE_FACTOR = 4000.0f;
    private static final float MIN_AMPLITUDE = 120.0f;
    private Paint linePaint;
    private List<Float> amplitudes;

    public AudioVisualizerView(Context context) {
        super(context);
        init(context, null);
    }

    public AudioVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AudioVisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        linePaint = new Paint();
        amplitudes = new ArrayList<>();

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.visualizer, 0, 0);
            try {
                int lineColor = a.getColor(R.styleable.visualizer_line_color, Color.WHITE);
                linePaint.setColor(lineColor);
            } finally {
                a.recycle();
            }
        } else {
            linePaint.setColor(Color.WHITE);
        }

        linePaint.setStrokeWidth(LINE_WIDTH);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        int centerY = height / 2;
        int centerX = width / 2;

        int numLines = width / (LINE_WIDTH + LINE_SPACE);

        for (int i = 0; i < numLines; i++) {
            int index = amplitudes.size() - numLines + i;
            if (index < 0) continue;

            float amplitude = amplitudes.get(index);
            amplitude = Math.max(amplitude, MIN_AMPLITUDE);
            float scaledAmplitude = amplitude / SCALE_FACTOR * ((float) height / 2);

            float startX = centerX + (i - (float) numLines / 2) * (LINE_WIDTH + LINE_SPACE);
            float startY = centerY - scaledAmplitude;
            float stopY = centerY + scaledAmplitude;

            canvas.drawLine(startX, startY, startX, stopY, linePaint);
        }
    }

    public void addAmplitude(float amplitude) {
        if (amplitudes.size() * (LINE_WIDTH + LINE_SPACE) >= getWidth()) {
            amplitudes.remove(0);
        }
        amplitudes.add(amplitude);
        invalidate();
    }

    public void clear() {
        amplitudes.clear();
        invalidate();
    }
}
