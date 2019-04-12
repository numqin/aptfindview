package com.qinlei.apt_library;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.view.View;

public class ProxyTool {
    //Activity
    @UiThread
    public static void bind(@NonNull Activity target) {
        View sourceView = target.getWindow().getDecorView();
        createBinding(target, sourceView);
    }

    //View
    @UiThread
    public static void bind(@NonNull View target) {
        createBinding(target, target);
    }

    //Fragment
    @UiThread
    public static void bind(@NonNull Object target, @NonNull View source) {
        createBinding(target, source);
    }

    public static final String SUFFIX = "$$Proxy";

    public static void createBinding(@NonNull Object target, @NonNull View root) {

        try {
            //生成类名+后缀名的代理类，并执行注入操作
            Class<?> targetClass = target.getClass();
            Class<?> proxyClass = Class.forName(targetClass.getName() + SUFFIX);
            IProxy proxy = (IProxy) proxyClass.newInstance();
            proxy.inject(target, root);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
