package com.qinlei.apt_processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

public class ProxyClass {
    /**
     * 类元素
     */
    public TypeElement mTypeElement;

    /**
     * 元素相关的辅助类
     */
    private Elements mElementUtils;

    /**
     * FieldViewBinding类型的集合
     */
    private Set<FieldViewBinding> bindViews = new HashSet<>();

    public ProxyClass(TypeElement mTypeElement, Elements mElementUtils) {
        this.mTypeElement = mTypeElement;
        this.mElementUtils = mElementUtils;
    }

    public void add(FieldViewBinding bindView) {
        bindViews.add(bindView);
    }

    //proxytool.IProxy
    public static final ClassName IPROXY = ClassName.get("com.qinlei.apt_library", "IProxy");
    //android.view.View
    public static final ClassName VIEW = ClassName.get("android.view", "View");
    //生成代理类的后缀名
    public static final String SUFFIX = "$$Proxy";

    /**
     * 用于生成代理类
     */
    public JavaFile generateProxy() {
        //生成public void inject(final T target, View root)方法
        MethodSpec.Builder injectMethodBuilder = MethodSpec.methodBuilder("inject")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(TypeName.get(mTypeElement.asType()), "target", Modifier.FINAL)
                .addParameter(VIEW, "root");

        //在inject方法中，添加我们的findViewById逻辑
        for (FieldViewBinding model : bindViews) {
            // find views
            injectMethodBuilder.addStatement("target.$N = ($T)(root.findViewById($L))", model.getVariableName(),
                    ClassName.get(model.getTypeMirror()), model.getResId());
        }


        // 添加以$$Proxy为后缀的类
        TypeSpec finderClass = TypeSpec.classBuilder(mTypeElement.getSimpleName() + SUFFIX)
                .addModifiers(Modifier.PUBLIC)
                //添加父接口
                .addSuperinterface(ParameterizedTypeName.get(IPROXY, TypeName.get(mTypeElement.asType())))
                //把inject方法添加到该类中
                .addMethod(injectMethodBuilder.build())
                .build();

        //添加包名
        String packageName = mElementUtils.getPackageOf(mTypeElement).getQualifiedName().toString();

        //生成Java文件
        return JavaFile.builder(packageName, finderClass).build();
    }
}
