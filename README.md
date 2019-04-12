---
title: 实现一个简单的APT
date: 2019-04-12 12:01:59
tags: 
- APT
categories: APT

---

如果还不清楚 APT 是什么请先看[这篇文章](https://numqin.github.io/2019/04/12/APT-%E6%98%AF%E4%BB%80%E4%B9%88/#more)，然后跟着这篇文章敲一遍，你大致就可以领悟如何实现一个 APT。

<!--more-->

> 项目是我找了两篇文章结合实现的，两篇文章当时没有记住链接，望见谅
>
> [github地址](https://github.com/numqin/aptfindview) ：主要是完成一个控件初始化的功能

### 项目结构

![apt_项目架构](https://numqin.github.io/2019/04/12/%E5%AE%9E%E7%8E%B0%E4%B8%80%E4%B8%AA%E7%AE%80%E5%8D%95%E7%9A%84APT/apt_1.jpg)

- APP 我们的主程序
- apt_annotation 定义注解类
- apt_processor 用于处理注解生成代码
- apt_library 通过调用生成的代码提供接口给 APP 调用

### 创建所有 module

- apt_annotation 属于 Java Library
- apt_processor 属于 Java Library
- apt_library 属于 Android Library

> 大家不要创建错了

### 添加依赖

结合上面的图我们可以了解它们之间的依赖关系

- apt_annotation 没有需要的依赖
- apt_processor 依赖于我们创建的 apt_annotation 和两个开源库 auto-service 、javapoet
- apt_library 依赖于我们创建的 apt_annotation
- App 依赖于我们创建的这三个 module : apt_annotation、apt_processor 和 apt_library

### 编写代码

处理完依赖后我们就可以开始编写代码了

**首先是所有库的最低层 apt_annotation ，我们定义一个注解**

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BindView {
    int value();
}
```

**之后我们开始编写 apt_processor 作用就是读取代码中的 `@BindView`，并生成我们的 Java 文件，给我们 apt_library 调用**

总共三个文件

BindViewProcessor 注解处理

```java
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
```

FieldViewBinding 需要注解的

```java
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
```

ProxyClass 用于生产 Java 代码的了里面记录了 FieldViewBinding 集合

```java
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
```

**最后编写我们的 apt_library ，通过反射调用我们生成的 java 文件完成控件的注入**

两个文件

IProxy

```java
public interface IProxy<T> {
    /**
     * @param target 所在的类
     * @param root   查找 View 的地方
     */
    public void inject(final T target, View root);
}
```

ProxyTool

```java
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
```

### App 中使用

```java
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.tv_hello)
    TextView tvHello;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ProxyTool.bind(this);
        tvHello.setText("Hello APT");
    }
}
```

我们可以看到我们使用 apt 自动生成的代码

```java
public class MainActivity$$Proxy implements IProxy<MainActivity> {
  @Override
  public void inject(final MainActivity target, View root) {
    target.tvHello = (TextView)(root.findViewById(2131165325));
  }
}
```

**至此一个简单的 apt 程序就完成了
