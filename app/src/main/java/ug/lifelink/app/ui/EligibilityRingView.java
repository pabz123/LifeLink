package ug.lifelink.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import ug.lifelink.app.R;

/**
 * A circular progress ring drawn with Canvas. On the Home hero card it shows how far a donor
 * is through the 90-day wait (a full ring means eligible) or, for hospital staff, how many
 * requests have been fulfilled.
 *
 * It is purely decorative: the numbers and words beside it carry the meaning, so the layout
 * marks it importantForAccessibility="no".
 */
public class EligibilityRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float strokeWidth;

    private float progress; // 0.0 (empty) to 1.0 (full)

    public EligibilityRingView(Context context) {
        this(context, null);
    }

    public EligibilityRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = getResources().getDimension(R.dimen.ring_stroke);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.hero_ring_track));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(context, R.color.hero_ring_progress));
    }

    /** Sets the filled fraction of the ring; values outside 0 to 1 are clamped. */
    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Inset by half the stroke so the thick line stays inside the view.
        float inset = strokeWidth / 2f;
        float size = Math.min(w - getPaddingLeft() - getPaddingRight(),
                h - getPaddingTop() - getPaddingBottom());
        float left = (w - size) / 2f + inset;
        float top = (h - size) / 2f + inset;
        bounds.set(left, top, left + size - strokeWidth, top + size - strokeWidth);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawOval(bounds, trackPaint);
        if (progress > 0f) {
            // -90 degrees is 12 o'clock; the arc grows clockwise from there.
            canvas.drawArc(bounds, -90f, 360f * progress, false, progressPaint);
        }
    }
}
