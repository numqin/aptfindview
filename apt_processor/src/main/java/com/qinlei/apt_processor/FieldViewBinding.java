package com.qinlei.apt_processor;

import com.qinlei.apt_annotation.BindView;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

public class FieldViewBinding {
    /**
     * 注解元素
     */
    private VariableElement mElement;

    /**
     * 资源id
     */
    private int mResId;

    /**
     * 变量名
     */
    private String mVariableName;

    /**
     * 变量类型
     */
    private TypeMirror mTypeMirror;

    public FieldViewBinding(Element element) {

        mElement = (VariableElement) element;
        BindView viewById = element.getAnnotation(BindView.class);
        //资源id
        mResId = viewById.value();
        //变量名
        mVariableName = element.getSimpleName().toString();
        //变量类型
        mTypeMirror = element.asType();
    }

    public VariableElement getElement() {
        return mElement;
    }

    public int getResId() {
        return mResId;
    }

    public String getVariableName() {
        return mVariableName;
    }

    public TypeMirror getTypeMirror() {
        return mTypeMirror;
    }
}
