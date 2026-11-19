package com.example.towncrierbd.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Waveform view matching TownCrierBD Voice capsule styling
 * (black rounded pill container with vertical white audio bars).
 */
public class WaveformView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bgRect   = new RectF();

    // Archetype waveform pattern reflecting sound variations
    private static final float[] REFERENCE_BARS = {
            0.18f, 0.28f, 0.45f, 0.30f, 0.65f, 0.40f, 0.25f, 0.55f,
            0.82f, 0.42f, 0.70f, 0.95f, 0.58f, 0.90f, 0.76f, 0.48f,
            0.88f, 0.62f, 0.38f, 0.72f, 0.85f, 0.52f, 0.75f, 0.35f,
            0.62f, 0.42f, 0.28f, 0.50f, 0.32f, 0.20f
    };

    private final List<Float> dynamicAmplitudes = new ArrayList<>();
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private float playbackProgress = 0f; // 0.0 to 1.0
    private boolean drawCapsuleBackground = false;

    private int barColor = Color.WHITE;
    private int capsuleBgColor = Color.parseColor("#0A0A0A");
    private float barWidthPx;
    private float barGapPx;

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setColor(barColor);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(capsuleBgColor);

        float density = getResources().getDisplayMetrics().density;
        barWidthPx = 3.2f * density;
        barGapPx   = 3.0f * density;
        barPaint.setStrokeWidth(barWidthPx);
    }

    public void setDrawCapsuleBackground(boolean draw) {
        this.drawCapsuleBackground = draw;
        invalidate();
    }

    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        invalidate();
    }

    public void setCapsuleBgColor(int color) {
        this.capsuleBgColor = color;
        bgPaint.setColor(color);
        invalidate();
    }

    public void setRecordingMode(boolean recording) {
        this.isRecording = recording;
        if (!recording) {
            dynamicAmplitudes.clear();
        }
        invalidate();
    }

    public void addLiveAmplitude(int rawAmplitude) {
        // rawAmplitude ranges 0..32767
        float norm = Math.max(0.12f, Math.min(1.0f, rawAmplitude / 16000f));
        dynamicAmplitudes.add(norm);

        // Keep maximum bars that fit on screen
        int maxBars = 34;
        if (getWidth() > 0) {
            float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            float stride = barWidthPx + barGapPx;
            if (stride > 0) {
                maxBars = (int) (availableWidth / stride);
            }
        }
        while (dynamicAmplitudes.size() > Math.max(15, maxBars)) {
            dynamicAmplitudes.remove(0);
        }
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void setPlaybackProgress(float progress) {
        this.playbackProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Draw black capsule background if requested
        if (drawCapsuleBackground) {
            bgRect.set(0, 0, w, h);
            float radius = h / 2f;
            canvas.drawRoundRect(bgRect, radius, radius, bgPaint);
        }

        // 2. Determine bars to render
        float[] bars;
        if (isRecording && !dynamicAmplitudes.isEmpty()) {
            bars = new float[dynamicAmplitudes.size()];
            for (int i = 0; i < dynamicAmplitudes.size(); i++) {
                bars[i] = dynamicAmplitudes.get(i);
            }
        } else {
            bars = REFERENCE_BARS;
        }

        float availableWidth = w - getPaddingLeft() - getPaddingRight();
        float stride = barWidthPx + barGapPx;
        int barCount = bars.length;

        // Auto-scale stride to fit nicely in available width
        float totalWidth = barCount * barWidthPx + (barCount - 1) * barGapPx;
        float startX = getPaddingLeft() + Math.max(0, (availableWidth - totalWidth) / 2f) + barWidthPx / 2f;

        float centerY = h / 2f;
        float maxBarHeight = (h - getPaddingTop() - getPaddingBottom()) * 0.78f;

        for (int i = 0; i < barCount; i++) {
            float x = startX + i * stride;
            if (x > w - getPaddingRight() - barWidthPx / 2f) break;

            float fraction = bars[i];
            float barHeight = Math.max(barWidthPx, fraction * maxBarHeight);
            float topY = centerY - barHeight / 2f;
            float bottomY = centerY + barHeight / 2f;

            // White color for bars
            barPaint.setColor(barColor);
            barPaint.setAlpha(255);
            canvas.drawLine(x, topY, x, bottomY, barPaint);
        }
    }
}
