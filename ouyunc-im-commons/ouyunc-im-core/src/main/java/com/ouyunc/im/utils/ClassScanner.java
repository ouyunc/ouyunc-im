package com.ouyunc.im.utils;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.*;

public class ClassScanner {

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);





    /**
     * 扫描指定包路径下所有指定类或接口的子类或实现类
     *
     * @param basePackage 指定的包路径
     * @param parentClass 指定的父类或接口
     * @return 符合要求的Class对象集合
     */
    public static Set<Class<?>> scanPackageBySuper(String basePackage, Class<?> parentClass) throws Exception {
        List<String> classNames = getClassNames(basePackage);
        if (classNames.isEmpty()) {
            return Collections.emptySet();
        }

        // 向线程池提交任务，并行扫描类
        List<Future<List<Class<?>>>> futures = new ArrayList<>();
        for (String className : classNames) {
            Future<List<Class<?>>> future = threadPool.submit(() -> {
                List<Class<?>> subClasses = new ArrayList<>();
                try {
                    Class<?> clazz = Class.forName(className);
                    if (parentClass.isAssignableFrom(clazz) && !clazz.equals(parentClass)
                            && !Modifier.isAbstract(clazz.getModifiers())) {
                        subClasses.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // 如果类不存在，则不做处理，直接返回空集合
                }
                return subClasses;
            });
            futures.add(future);
        }

        // 等待所有任务执行完毕，并合并子任务结果
        Set<Class<?>> subClasses = new HashSet<>();
        for (Future<List<Class<?>>> future : futures) {
            subClasses.addAll(future.get());
        }
        return subClasses;
    }

    /**
     * 获取指定包路径下的所有类名
     */
    private static List<String> getClassNames(String basePackage) throws Exception {
        String packagePath = basePackage.replace(".", "/");
        Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(packagePath);
        List<String> classNames = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
            File file = new File(filePath);
            if (!file.isDirectory()) {
                continue;
            }
            String[] fileNames = file.list();
            for (String fileName : fileNames) {
                if (fileName.endsWith(".class")) {
                    String className = basePackage + "." + fileName.substring(0, fileName.length() - 6);
                    classNames.add(className);
                } else if (fileName.endsWith(".jar")) {
                    URL jarUrl = new URL("jar:file:" + filePath + "/" + fileName + "!/");
                    URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl});
                    classNames.addAll(getClassNamesFromJar(loader, basePackage));
                }
            }
        }
        return classNames;
    }

    /**
     * 从指定jar包中获取指定包路径下的所有类名
     */
    private static List<String> getClassNamesFromJar(URLClassLoader loader, String basePackage) throws Exception {
        String packagePath = basePackage.replace(".", "/") + "/";
        Enumeration<URL> urls = loader.findResources(packagePath);
        List<String> classNames = new ArrayList<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
            if (!filePath.startsWith("file:")) {
                continue;
            }
            File file = new File(filePath.substring(5));
            if (!file.isFile()) {
                continue;
            }
            classNames.addAll(getClassNamesFromJarFile(loader, file, basePackage));
        }
        return classNames;
    }

    /**
     * 从指定jar文件中获取指定包路径下的所有类名
     */
    private static List<String> getClassNamesFromJarFile(URLClassLoader loader, File jarFile, String basePackage)
            throws Exception {
        List<String> classNames = new ArrayList<>();
        try (URLClassLoader jarLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, loader)) {
            String packagePath = basePackage.replace(".", "/") + "/";
            Enumeration<URL> urls = jarLoader.findResources(packagePath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String classPath = url.getFile();
                if (classPath.endsWith(".class")) {
                    String className = classPath.substring(0, classPath.length() - 6).replace("/", ".");
                    classNames.add(className);
                }
            }
        }
        return classNames;
    }
}
