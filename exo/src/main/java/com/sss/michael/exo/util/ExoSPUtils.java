package com.sss.michael.exo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * @author Michael by 61642
 * @date 2023/3/6 14:23
 * @Description SharedPreferences操作类
 */
public final class ExoSPUtils {
    private static SharedPreferences sp;

    public void init(Context context, String spName, int mode) {
        sp = context.getSharedPreferences(spName, mode);
    }

    public Editor getEditor() {
        return sp.edit();
    }

    public void put(String key, String value) {
        getEditor().putString(key, value).apply();
    }

    public String getString(String key) {
        return this.getString(key, (String) null);
    }

    public String getString(String key, String defaultValue) {
        return sp.getString(key, defaultValue);
    }

    public void put(String key, int value) {
        getEditor().putInt(key, value).apply();
    }

    public int getInt(String key) {
        return this.getInt(key, -1);
    }

    public int getInt(String key, int defaultValue) {
        return sp.getInt(key, defaultValue);
    }

    public void put(String key, long value) {
        getEditor().putLong(key, value).apply();
    }

    public long getLong(String key) {
        return this.getLong(key, -1L);
    }

    public long getLong(String key, long defaultValue) {
        return sp.getLong(key, defaultValue);
    }

    public void put(String key, float value) {
        getEditor().putFloat(key, value).apply();
    }

    public float getFloat(String key) {
        return this.getFloat(key, -1.0F);
    }

    public float getFloat(String key, float defaultValue) {
        return sp.getFloat(key, defaultValue);
    }

    public void put(String key, boolean value) {
        getEditor().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key) {
        return this.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return sp.getBoolean(key, defaultValue);
    }

    public void put(String key, Set<String> values) {
        getEditor().putStringSet(key, values).apply();
    }

    public Set<String> getStringSet(String key) {
        return this.getStringSet(key, (Set) null);
    }

    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        return sp.getStringSet(key, defaultValue);
    }

    public void put(String key, double value) {
        getEditor().putString(key, value + "").apply();
    }

    public double getDouble(String key) {
        try {
            String value = this.getString(key, "0.0");
            return Double.parseDouble(value);
        } catch (Exception var3) {
            return 0.0D;
        }
    }

    public Map<String, ?> getAll() {
        return sp.getAll();
    }

    public void remove(String key) {
        getEditor().remove(key).apply();
    }

    public boolean contains(String key) {
        return sp.contains(key);
    }

    public void clear() {
        getEditor().clear().apply();
    }

    public static void setSP(Context context, String key, Object object) {
        SharedPreferences sp = context.getSharedPreferences(getSpName(context), 0);
        Editor editor = sp.edit();
        if (object instanceof String) {
            editor.putString(key, (String) object);
        } else if (object instanceof Integer) {
            editor.putInt(key, (Integer) object);
        } else if (object instanceof Boolean) {
            editor.putBoolean(key, (Boolean) object);
        } else if (object instanceof Float) {
            editor.putFloat(key, (Float) object);
        } else if (object instanceof Long) {
            editor.putLong(key, (Long) object);
        } else {
            editor.putString(key, object.toString());
        }

        SharedPreferencesCompat.apply(editor);
    }

    private static String getSpName(Context context) {
        return context.getPackageName() + "_preferences";
    }

    public static Object getSP(Context context, String key, Object defaultObject) {
        SharedPreferences sp = context.getSharedPreferences(getSpName(context), 0);
        if (defaultObject instanceof String) {
            return sp.getString(key, (String) defaultObject);
        } else if (defaultObject instanceof Integer) {
            return sp.getInt(key, (Integer) defaultObject);
        } else if (defaultObject instanceof Boolean) {
            return sp.getBoolean(key, (Boolean) defaultObject);
        } else if (defaultObject instanceof Float) {
            return sp.getFloat(key, (Float) defaultObject);
        } else {
            return defaultObject instanceof Long ? sp.getLong(key, (Long) defaultObject) : null;
        }
    }

    private static class SharedPreferencesCompat {
        private static final Method sApplyMethod = findApplyMethod();

        private SharedPreferencesCompat() {
        }

        private static Method findApplyMethod() {
            try {
                Class clz = Editor.class;
                return clz.getMethod("apply");
            } catch (NoSuchMethodException var1) {
                var1.printStackTrace();
                return null;
            }
        }

        public static void apply(Editor editor) {
            try {
                if (sApplyMethod != null) {
                    sApplyMethod.invoke(editor);
                    return;
                }
            } catch (IllegalArgumentException var2) {
                var2.printStackTrace();
            } catch (IllegalAccessException var3) {
                var3.printStackTrace();
            } catch (InvocationTargetException var4) {
                var4.printStackTrace();
            }

            editor.commit();
        }
    }
}
