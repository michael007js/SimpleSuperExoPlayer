package cn.sss.michael.exo.component;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;

import cn.sss.michael.exo.callback.IExoControlComponent;

import java.util.List;

/**
 * @author Michael by SSS
 * @date 2025/12/26 0026 0:32
 * @Description 组件基类
 */
public abstract class BaseExoControlComponent<B extends ViewDataBinding> extends IExoControlComponentLayout {

    protected B binding;


    public BaseExoControlComponent(@NonNull Context context) {
        this(context, null);
    }

    public BaseExoControlComponent(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseExoControlComponent(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        binding = DataBindingUtil.inflate(LayoutInflater.from(context), setLayout(), this, true);
        init(context);
    }

    /**
     * 设置布局
     */
    protected abstract int setLayout();


    /**
     * 初始化
     */
    protected abstract void init(Context context);


    @Nullable
    @Override
    public View getView() {
        return this;
    }

    @Override
    public List<IExoControlComponent> getExoComponents() {
        return exoControllerWrapper.getExoComponents();
    }

    @Override
    public <T extends IExoControlComponent> T getExoControlComponentByClass(Class<T> cls) {
        return exoControllerWrapper.getExoControlComponentByClass(cls);
    }

}
