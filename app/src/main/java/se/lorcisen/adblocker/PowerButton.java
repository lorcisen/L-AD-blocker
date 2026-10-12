package se.lorcisen.adblocker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** The large animated on/off button on the home screen. */
final class PowerButton extends View {
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint faceEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix rotation = new Matrix();
    private SweepGradient sweep;
    private float progress;
    private float glowProgress = -1;
    private boolean on;
    private ValueAnimator animator;

    PowerButton(Context c) {
        super(c);
        float stroke = Ui.dp(c, 7);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Ui.SURFACE_2);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setStrokeCap(Paint.Cap.ROUND);
        pulse.setStyle(Paint.Style.STROKE);
        pulse.setStrokeWidth(Ui.dp(c, 2));
        faceEdge.setStyle(Paint.Style.STROKE);
        faceEdge.setStrokeWidth(Ui.dp(c, 1));
        faceEdge.setColor(Ui.STROKE);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeWidth(Ui.dp(c, 6));
        setClickable(true);
        setFocusable(true);
        setContentDescription("Slå på eller av reklamblockering");
    }

    void setOn(boolean value, boolean animate) {
        if (value == on && (animator == null || !animator.isRunning())) {
            if (!animate) progress = on ? 1 : 0;
            return;
        }
        on = value;
        if (animator != null) animator.cancel();
        if (!animate) {
            progress = on ? 1 : 0;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, on ? 1 : 0);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float cx = w / 2f, cy = h / 2f;
        sweep = new SweepGradient(cx, cy,
                new int[] {Ui.ACCENT, Ui.ACCENT_2, Ui.ACCENT_3, Ui.ACCENT},
                new float[] {0f, 0.4f, 0.75f, 1f});
        float r = faceRadius(w, h);
        glowProgress = -1;
        face.setShader(new LinearGradient(0, cy - r, 0, cy + r,
                0xFF1C2638, 0xFF111827, Shader.TileMode.CLAMP));
    }

    private static float faceRadius(int w, int h) {
        return Math.min(w, h) / 2f * 0.56f;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = faceRadius(w, h);
        float ringR = r + Ui.dp(getContext(), 18);
        long now = SystemClock.uptimeMillis();

        if (progress > 0.01f) {
            if (glowProgress != progress) {
                glowProgress = progress;
                int a = (int) (0x50 * progress);
                glow.setShader(new RadialGradient(cx, cy, ringR * 1.45f,
                    new int[] {(a << 24) | (Ui.ACCENT & 0xFFFFFF), (a / 3 << 24) | (Ui.ACCENT_2 & 0xFFFFFF), 0},
                    new float[] {0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            }
            canvas.drawCircle(cx, cy, ringR * 1.45f, glow);

            // Two slow, expanding "sonar" rings while protection is on.
            for (int i = 0; i < 2; i++) {
                float t = ((now + i * 1600) % 3200) / 3200f;
                float pr = ringR + t * Ui.dp(getContext(), 34);
                pulse.setColor(Ui.withAlpha(Ui.ACCENT, (int) (110 * (1 - t) * progress)));
                canvas.drawCircle(cx, cy, pr, pulse);
            }
        }

        canvas.drawCircle(cx, cy, ringR, track);
        if (progress > 0.001f) {
            rotation.setRotate((now / 12f) % 360, cx, cy);
            sweep.setLocalMatrix(rotation);
            ring.setShader(sweep);
            oval.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(oval, -90, 360 * progress, false, ring);
        }

        float scale = isPressed() ? 0.95f : 1f;
        canvas.save();
        canvas.scale(scale, scale, cx, cy);
        canvas.drawCircle(cx, cy, r, face);
        canvas.drawCircle(cx, cy, r, faceEdge);

        // Power symbol.
        icon.setColor(Ui.blend(0xFF475569, Ui.ACCENT, progress));
        float ir = r * 0.34f;
        oval.set(cx - ir, cy - ir, cx + ir, cy + ir);
        canvas.drawArc(oval, -58, 296, false, icon);
        canvas.drawLine(cx, cy - ir * 1.25f, cx, cy - ir * 0.15f, icon);
        canvas.restore();

        if (progress > 0.001f && isShown()) postInvalidateOnAnimation();
    }
}
