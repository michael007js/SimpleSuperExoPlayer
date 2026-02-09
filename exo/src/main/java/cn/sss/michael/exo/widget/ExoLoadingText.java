package cn.sss.michael.exo.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import cn.sss.michael.exo.R;
import cn.sss.michael.exo.util.ExoDensityUtil;

/**
 * LoadingText 极致高级版 + 呼吸缩放
 * 1. 模拟物理光泽：双重混合渐变 + 30度斜切
 * 2. 呼吸式底色：增加暗部细节
 * 3. 动态缩放：流光经过时产生微小的放大效果
 */
public class ExoLoadingText extends View {

    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "加载中...";

    // 颜色配置
    private int mTextColor = Color.parseColor("#55FFFFFF"); // 深邃暗底色
    private int mHighlightColor = Color.parseColor("#ED4040"); // 主色

    private float mTextSize = 60f;
    private float mPercent = 0f;
    private float mScale = 1.0f; // 缩放比例
    private boolean isAutoStartAnim;
    private int animDuration = 2800;

    private PorterDuffXfermode mXfermode = new PorterDuffXfermode(PorterDuff.Mode.SRC_IN);
    private ValueAnimator mAnimator;

    private LinearGradient mLinearGradient;
    private Matrix mGradientMatrix = new Matrix();
    private Rect rect = new Rect();

    public ExoLoadingText(Context context) {
        this(context, null);
    }

    public ExoLoadingText(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoLoadingText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mPaint.setLetterSpacing(0.1f);
//        mPaint.setFakeBoldText(false);
        mPaint.setFakeBoldText(true);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ExoLoadingText, defStyleAttr, 0);
        String t = a.getString(R.styleable.ExoLoadingText_exo_drawText);
        if (t != null && !t.isEmpty()) {
            text = t;
        }
        mTextColor = a.getColor(R.styleable.ExoLoadingText_exo_drawTextColor, mTextColor);
        mTextSize = a.getDimensionPixelSize(R.styleable.ExoLoadingText_exo_drawTextSize, ExoDensityUtil.dp2px(context, 30));
        mHighlightColor = a.getColor(R.styleable.ExoLoadingText_exo_mask_view_color, Color.parseColor("#e9302d"));
        isAutoStartAnim = a.getBoolean(R.styleable.ExoLoadingText_exo_isAutoStartAnim, true);
        animDuration = a.getInteger(R.styleable.ExoLoadingText_exo_animDuration, animDuration);
        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mPaint.setTextSize(mTextSize);
        mPaint.getTextBounds(text, 0, text.length(), rect);
        // 预留缩放空间，防止放大时切边
        int w = rect.width() + getPaddingLeft() + getPaddingRight() + 100;
        int h = rect.height() + getPaddingTop() + getPaddingBottom() + 60;
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;

        Paint.FontMetrics fm = mPaint.getFontMetrics();
        float baseline = centerY - (fm.ascent + fm.descent) / 2;

        // 保存画布状态并应用中心缩放
        canvas.save();
        canvas.scale(mScale, mScale, centerX, centerY);

        // 绘制底层阴影文字
        mPaint.setShader(null);
        mPaint.setXfermode(null);
        mPaint.setColor(mTextColor);
        canvas.drawText(text, centerX, baseline, mPaint);

        // 准备流光渐变
        if (mLinearGradient == null) {
            mLinearGradient = new LinearGradient(
                    0, 0, width * 0.7f, 0,
                    new int[]{
                            0x00FFFFFF,
                            mHighlightColor,
                            Color.parseColor("#FFFFFFFF"), // 中心点高光感
                            mHighlightColor,
                            0x00FFFFFF
                    },
                    new float[]{0f, 0.35f, 0.5f, 0.65f, 1f},
                    Shader.TileMode.CLAMP);
        }

        // 计算流光矩阵偏移与斜切
        float gradientX = -width + (width * 2) * mPercent;
        mGradientMatrix.reset();
        mGradientMatrix.setTranslate(gradientX, 0);
        mGradientMatrix.preSkew(-0.4f, 0);
        mLinearGradient.setLocalMatrix(mGradientMatrix);

        // 离屏渲染文字流光
        int saveCount = canvas.saveLayer(0, 0, width, height, null);

        mPaint.setShader(null);
        mPaint.setColor(Color.WHITE);
        canvas.drawText(text, centerX, baseline, mPaint);

        mPaint.setXfermode(mXfermode);
        mPaint.setShader(mLinearGradient);
        canvas.drawRect(0, 0, width, height, mPaint);

        mPaint.setXfermode(null);
        mPaint.setShader(null);
        canvas.restoreToCount(saveCount);

        // 恢复缩放画布
        canvas.restore();
    }

    public void startAnim() {
        if (mAnimator != null && mAnimator.isRunning()) return;

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(animDuration);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mPercent = (float) animation.getAnimatedValue();

            // 计算呼吸缩放逻辑：
            // 在 mPercent = 0.5 (即流光在中心) 时，mScale 达到最大值 1.08
            // 之后回落到 1.0
            float sinValue = (float) Math.sin(mPercent * Math.PI);
            mScale = 1.0f + (sinValue * 0.03f);

            invalidate();
        });
        mAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isAutoStartAnim) {
            startAnim();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
            mAnimator.removeAllListeners();
        }
        super.onDetachedFromWindow();
    }
}