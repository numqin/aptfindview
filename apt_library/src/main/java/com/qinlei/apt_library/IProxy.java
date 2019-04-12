package com.qinlei.apt_library;

import android.view.View;

public interface IProxy<T> {
    /**
     * @param target 所在的类
     * @param root   查找 View 的地方
     */
    public void inject(final T target, View root);
}
