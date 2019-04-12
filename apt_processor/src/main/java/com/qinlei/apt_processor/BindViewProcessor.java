package com.qinlei.apt_processor;

import com.google.auto.service.AutoService;
import com.qinlei.apt_annotation.BindView;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.STATIC;

@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {

    private Filer mFiler;
    private Messager mMessager;
    private Elements mElementUtils;

    /**
     * 初始化,可以得到ProcessingEnviroment，ProcessingEnviroment提供很多有用的工具类Elements, Types 和 Filer
     *
     * @param processingEnvironment
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFiler = processingEnvironment.getFiler();
        mMessager = processingEnvironment.getMessager();
        mElementUtils = processingEnvironment.getElementUtils();
    }

    /**
     * 指定这个注解处理器是注册给哪个注解的，这里说明是注解BindView
     *
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(BindView.class.getCanonicalName());
        return supportTypes;
    }

    /**
     * 指定使用的Java版本，通常这里返回SourceVersion.latestSupported()
     *
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 可以在这里写扫描、评估和处理注解的代码，生成Java文件
     *
     * @param set
     * @param roundEnvironment
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(BindView.class)) {
            if (!isValid(BindView.class, "field", element)) {
                return true;
            }
            parseViewById(element);
        }

        //为每个宿主类生成所对应的代理类
        for (ProxyClass proxyClass_ : mProxyClassMap.values()) {
            try {
                proxyClass_.generateProxy().writeTo(mFiler);
            } catch (IOException e) {
                error(null, e.getMessage());
            }
        }
        mProxyClassMap.clear();
        return true;
    }

    private boolean isValid(Class<? extends Annotation> annotationClass, String targetThing, Element element) {
        boolean isVaild = true;

        //获取变量的所在的父元素，肯能是类、接口、枚举
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        //父元素的全限定名
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        // 所在的类不能是private或static修饰
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(PRIVATE) || modifiers.contains(STATIC)) {
            error(element, "@%s %s must not be private or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            isVaild = false;
        }

        // 父元素必须是类，而不能是接口或枚举
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            error(enclosingElement, "@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            isVaild = false;
        }

        //不能在Android框架层注解
        if (qualifiedName.startsWith("android.")) {
            error(element, "@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return false;
        }
        //不能在java框架层注解
        if (qualifiedName.startsWith("java.")) {
            error(element, "@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return false;
        }

        return isVaild;
    }

    private void error(Element e, String msg, Object... args) {
        mMessager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    private Map<String, ProxyClass> mProxyClassMap = new HashMap<>();

    /**
     * 处理ViewById注解
     *
     * @param element
     */
    private void parseViewById(Element element) {
        ProxyClass proxyClass = getProxyClass(element);
        //把被注解的view对象封装成一个model，放入代理类的集合中
        FieldViewBinding bindView = new FieldViewBinding(element);
        proxyClass.add(bindView);
    }

    /**
     * 生成或获取注解元素所对应的ProxyClass类
     */
    private ProxyClass getProxyClass(Element element) {
        //被注解的变量所在的类
        TypeElement classElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = classElement.getQualifiedName().toString();
        ProxyClass proxyClass = mProxyClassMap.get(qualifiedName);
        if (proxyClass == null) {
            //生成每个宿主类所对应的代理类，后面用于生产java文件
            proxyClass = new ProxyClass(classElement, mElementUtils);
            mProxyClassMap.put(qualifiedName, proxyClass);
        }
        return proxyClass;
    }
}
