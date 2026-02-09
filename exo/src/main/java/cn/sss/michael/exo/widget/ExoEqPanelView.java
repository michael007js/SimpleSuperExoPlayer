package cn.sss.michael.exo.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import cn.sss.michael.exo.ExoConfig;
import cn.sss.michael.exo.R;
import cn.sss.michael.exo.constant.ExoEqualizerPreset;
import cn.sss.michael.exo.helper.ExoVibratorHelper;
import cn.sss.michael.exo.util.ExoDensityUtil;

import java.util.Locale;

/**
 * @author Michael by SSS
 * @date 2026/1/5 0005 21:57
 * @Description 10段均衡器面板
 */
public class ExoEqPanelView extends View {

    private int mMainColor;
    private final String[] mFreqLabelTexts = new String[ExoConfig.EQ_BAND_COUNT];
    private Scroller mScroller;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;

    private float mOffsetX = 0;
    private float mMaxOffsetX;

    private float[] bandGains = ExoEqualizerPreset.CUSTOM.getGains();
    private int activeBand = -1;
    private boolean mIsScrolling, mIsAdjustingGain;
    private float mLastX;

    private Paint gridPaint, curvePaint, shadowPaint, pointPaint, textPaint, commentPaint;
    private float paddingLeftRight, paddingTop, contentHeight, mBandSpacing;

    private OnEqChangeListener listener;

    public ExoEqPanelView(Context context) {
        this(context, null);
    }

    public ExoEqPanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mMainColor = ContextCompat.getColor(context, R.color.exo_colorAccent);
        mScroller = new Scroller(getContext());
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        paddingLeftRight = ExoDensityUtil.dp2px(getContext(), 50);
        paddingTop = ExoDensityUtil.dp2px(getContext(), 30);
        mBandSpacing = ExoDensityUtil.dp2px(getContext(), 80);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        commentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 网格线：主色调 15% 透明度
        gridPaint.setColor(ColorUtils.setAlphaComponent(mMainColor, 38));
        gridPaint.setStrokeWidth(ExoDensityUtil.dp2px(getContext(), 1f));

        // 曲线
        curvePaint.setColor(mMainColor);
        curvePaint.setStrokeWidth(ExoDensityUtil.dp2px(getContext(), 2.5f));
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);

        // 阴影
        shadowPaint.setStyle(Paint.Style.FILL);

        // 圆点
        pointPaint.setColor(Color.WHITE);

        // 主文本
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(ExoDensityUtil.dp2px(getContext(), 11));

        // 注释文本：白色 60% 透明度
        commentPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 153));
        commentPaint.setTextSize(ExoDensityUtil.dp2px(getContext(), 9));

        // 初始化频率文本
        for (int i = 0; i < ExoConfig.EQ_BAND_COUNT; i++) {
            float freq = ExoConfig.EQ_CENTER_FREQUENCIES[i];
            if (freq >= 1000) {
                mFreqLabelTexts[i] = (freq % 1000 == 0) ?
                        String.format(Locale.US, "%dkHz", (int) (freq / 1000)) :
                        String.format(Locale.US, "%.1fkHz", freq / 1000f);
            } else {
                mFreqLabelTexts[i] = (freq == (int) freq) ?
                        String.format(Locale.US, "%dHz", (int) freq) :
                        String.format(Locale.US, "%.2fHz", freq);
            }
        }
    }

    public void setBandGains(float[] bandGains, boolean notifyCallback) {
        System.arraycopy(bandGains, 0, this.bandGains, 0, bandGains.length);
        if (notifyCallback) {
            if (listener != null) {
                listener.onAllGainsBandGainChanged(this.bandGains);
            }
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        contentHeight = h - paddingTop - ExoDensityUtil.dp2px(getContext(), 30);
        float totalWidth = (ExoConfig.EQ_BAND_COUNT - 1) * mBandSpacing + paddingLeftRight * 2;
        mMaxOffsetX = Math.max(0, totalWidth - w);

        if (contentHeight <= 0) return;
        // 渐变：从主色的 30% 透明度到完全透明
        int startColor = ColorUtils.setAlphaComponent(mMainColor, 155); // 60% alpha
        int endColor = ColorUtils.setAlphaComponent(mMainColor, 0);    // 0% alpha

        shadowPaint.setShader(new LinearGradient(0, paddingTop, 0, paddingTop + contentHeight,
                startColor, endColor, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // 绘制静态标签
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setAlpha(150);
        canvas.drawText(ExoConfig.EQ_MAX_DB + "", ExoDensityUtil.dp2px(getContext(), 10), gainToY(ExoConfig.EQ_MAX_DB), textPaint);
        canvas.drawText("0", ExoDensityUtil.dp2px(getContext(), 10), gainToY(0), textPaint);
        canvas.drawText(ExoConfig.EQ_MIN_DB + "", ExoDensityUtil.dp2px(getContext(), 10), gainToY(ExoConfig.EQ_MIN_DB), textPaint);
        textPaint.setAlpha(255);

        canvas.save();
        canvas.translate(-mOffsetX, 0);

        // 绘制网格
        float x0 = bandX(0), x1 = bandX(ExoConfig.EQ_BAND_COUNT - 1);
        canvas.drawLine(x0, gainToY(ExoConfig.EQ_MAX_DB), x1, gainToY(ExoConfig.EQ_MAX_DB), gridPaint);
        canvas.drawLine(x0, gainToY(0), x1, gainToY(0), gridPaint);
        canvas.drawLine(x0, gainToY(ExoConfig.EQ_MIN_DB), x1, gainToY(ExoConfig.EQ_MIN_DB), gridPaint);

        // 绘制曲线和阴影
        Path path = new Path();
        path.moveTo(bandX(0), gainToY(bandGains[0]));
        for (int i = 0; i < ExoConfig.EQ_BAND_COUNT - 1; i++) {
            float x00 = bandX(i), y0 = gainToY(bandGains[i]);
            float x11 = bandX(i + 1), y1 = gainToY(bandGains[i + 1]);
            path.cubicTo(x00 + mBandSpacing / 2, y0, x11 - mBandSpacing / 2, y1, x11, y1);
        }
        Path shadow = new Path(path);
        shadow.lineTo(bandX(ExoConfig.EQ_BAND_COUNT - 1), paddingTop + contentHeight);
        shadow.lineTo(bandX(0), paddingTop + contentHeight);
        shadow.close();
        canvas.drawPath(shadow, shadowPaint);
        canvas.drawPath(path, curvePaint);

        // 绘制圆点
        for (int i = 0; i < ExoConfig.EQ_BAND_COUNT; i++) {
            // 选中点全亮，未选中点 60% 透明
            pointPaint.setAlpha(i == activeBand ? 255 : 153);
            canvas.drawCircle(bandX(i), gainToY(bandGains[i]),
                    i == activeBand ? ExoDensityUtil.dp2px(getContext(), 7) : ExoDensityUtil.dp2px(getContext(), 5),
                    pointPaint);
        }
        // 绘制频率和备注
        textPaint.setTextAlign(Paint.Align.CENTER);
        commentPaint.setTextAlign(Paint.Align.CENTER);
        float baseY = paddingTop + contentHeight + ExoDensityUtil.dp2px(getContext(), 22);

        for (int i = 0; i < ExoConfig.EQ_BAND_COUNT; i++) {
            float x = bandX(i);
            canvas.drawText(mFreqLabelTexts[i], x, baseY, textPaint);
            canvas.drawText(ExoConfig.EQ_FREQ_COMMENTS[i], x, baseY + ExoDensityUtil.dp2px(getContext(), 15), commentPaint);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);
        float x = event.getX(), y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mLastX = x;
                mIsScrolling = false;
                activeBand = findTouchedBand(x, y);
                if (activeBand != -1) {
                    mIsAdjustingGain = true;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = mLastX - x;
                mLastX = x;

                if (mIsAdjustingGain) {
                    float newGain = yToGain(y);

                    ExoVibratorHelper.processEqVibrate(getContext(), newGain, ExoConfig.EQ_MIN_DB, ExoConfig.EQ_MAX_DB);
                    bandGains[activeBand] = yToGain(y);
                    if (listener != null) {
                        listener.onBandGainChanged(activeBand, bandGains[activeBand]);
                        listener.onAllGainsBandGainChanged(bandGains);
                    }
                    invalidate();
                } else {
                    if (!mIsScrolling && Math.abs(dx) > mTouchSlop) mIsScrolling = true;
                    if (mIsScrolling) {
                        mOffsetX = Math.max(0, Math.min(mOffsetX + dx, mMaxOffsetX));
                        invalidate();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsScrolling) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    mScroller.fling((int) mOffsetX, 0, (int) -mVelocityTracker.getXVelocity(), 0, 0, (int) mMaxOffsetX, 0, 0);
                    invalidate();
                }
                activeBand = -1;
                mIsScrolling = mIsAdjustingGain = false;
                invalidate();
                break;
        }
        return true;
    }

    private int findTouchedBand(float touchX, float touchY) {
        float logicalX = touchX + mOffsetX;
        for (int i = 0; i < ExoConfig.EQ_BAND_COUNT; i++) {
            float bx = bandX(i);
            float screenX = bx - mOffsetX;
            if (screenX < 0 || screenX > getWidth()) continue;
            if (Math.abs(logicalX - bx) < ExoDensityUtil.dp2px(getContext(), 40) &&
                    Math.abs(touchY - gainToY(bandGains[i])) < ExoDensityUtil.dp2px(getContext(), 40))
                return i;
        }
        return -1;
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mOffsetX = mScroller.getCurrX();
            invalidate();
        }
    }

    private float bandX(int i) {
        return paddingLeftRight + i * mBandSpacing;
    }

    private float gainToY(float g) {
        return paddingTop + (1f - (g - ExoConfig.EQ_MIN_DB) / (ExoConfig.EQ_MAX_DB - ExoConfig.EQ_MIN_DB)) * contentHeight;
    }

    private float yToGain(float y) {
        float r = 1f - (y - paddingTop) / contentHeight;
        return Math.max(ExoConfig.EQ_MIN_DB, Math.min(ExoConfig.EQ_MAX_DB, ExoConfig.EQ_MIN_DB + r * (ExoConfig.EQ_MAX_DB - ExoConfig.EQ_MIN_DB)));
    }

    public void setOnEqChangeListener(OnEqChangeListener l) {
        this.listener = l;
    }

    public interface OnEqChangeListener {
        void onBandGainChanged(int band, float gain);

        void onAllGainsBandGainChanged(float[] allGains);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ExoVibratorHelper.reset();
    }
}