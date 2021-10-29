package com.ouyu.im.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 获取接口的所有实现类 理论上也可以用来获取类的所有子类
 * 查询路径有限制，只局限于接口所在模块下，比如pandora-gateway,而非整个pandora（会递归搜索该文件夹下所以的实现类）
 * 路径中不可含中文，否则会异常。若要支持中文路径，需对该模块代码中url.getPath() 返回值进行urldecode.
 */
public class ClassUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ClassUtil.class);

    // 整个项目路径查找
    public static ArrayList<Class> getAllClassByInterface(Class clazz) {
        ArrayList<Class> list = new ArrayList<>();
        // 判断是否是一个接口
        if (clazz.isInterface()) {
            try {
                ArrayList<Class> allClass = getAllClass(clazz.getPackage().getName(), false);
                /**
                 * 循环判断路径下的所有类是否实现了指定的接口 并且排除接口类自己
                 */
                for (int i = 0; i < allClass.size(); i++) {
                    /**
                     * 判断是不是同一个接口
                     */
                    // isAssignableFrom:判定此 Class 对象所表示的类或接口与指定的 Class
                    // 参数所表示的类或接口是否相同，或是否是其超类或超接口
                    Class aClass = allClass.get(i);
                    if (clazz.isAssignableFrom(aClass)) {
                        // 排除自身以及抽象类
                        if (!clazz.equals(aClass) && !Modifier.isAbstract(aClass.getModifiers())) {
                            // 自身并不加进去
                            list.add(aClass);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("出现异常{}",e.getMessage());
                throw new RuntimeException("出现异常"+e.getMessage());
            }
        }
        LOG.info("class list size :"+list.size());
        return list;
    }

    // 当前接口所在的包以及子包路径下
    public static ArrayList<Class> getAllClassByInterface(Class clazz, boolean onlyInterfacePath) {
        ArrayList<Class> list = new ArrayList<>();
        // 判断是否是一个接口
        if (clazz.isInterface()) {
            try {
                ArrayList<Class> allClass = getAllClass(clazz.getPackage().getName(), onlyInterfacePath);
                /**
                 * 循环判断路径下的所有类是否实现了指定的接口 并且排除接口类自己
                 */
                for (int i = 0; i < allClass.size(); i++) {
                    /**
                     * 判断是不是同一个接口
                     */
                    // isAssignableFrom:判定此 Class 对象所表示的类或接口与指定的 Class
                    // 参数所表示的类或接口是否相同，或是否是其超类或超接口
                    Class aClass = allClass.get(i);
                    if (clazz.isAssignableFrom(aClass)) {
                        // 排除自身以及抽象类
                        if (!clazz.equals(aClass) && !Modifier.isAbstract(aClass.getModifiers())) {
                            // 自身并不加进去
                            list.add(aClass);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("出现异常{}",e.getMessage());
                throw new RuntimeException("出现异常"+e.getMessage());
            }
        }
        LOG.info("class list size :"+list.size());
        return list;
    }


    /**
     * 从一个指定路径下查找所有的类
     *
     * @param packagename
     */
    private static ArrayList<Class> getAllClass(String packagename, boolean onlyInterfacePath) {


        LOG.info("packageName to search："+packagename);
       List<String> classNameList =  getClassName(packagename, onlyInterfacePath);
        ArrayList<Class> list = new ArrayList<>();

        for(String className : classNameList){
            try {
                list.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                LOG.error("load class from name failed:"+className+e.getMessage());
                throw new RuntimeException("load class from name failed:"+className+e.getMessage());
            }
        }
        LOG.info("find list size :"+list.size());
        return list;
    }

    /**
     * 获取某包下所有类
     * @param packageName 包名
     * @return 类的完整名称
     */
    private static List<String> getClassName(String packageName, boolean onlyInterfacePath) {

        List<String> fileNames = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String packagePath = packageName.replace(".", "/");
        URL url = loader.getResource(packagePath);
        if (url != null) {
            String type = url.getProtocol();
            LOG.debug("file type : " + type);
            if (type.equals("file")) {
                String fileSearchPath = url.getPath();
                LOG.debug("fileSearchPath: "+fileSearchPath);
                if (!onlyInterfacePath) {
                    fileSearchPath = fileSearchPath.substring(0,fileSearchPath.indexOf("/classes"));
                }
                LOG.debug("fileSearchPath: "+fileSearchPath);
                fileNames = getClassNameByFile(fileSearchPath);
            } else if (type.equals("jar")) {
                try{
                    JarURLConnection jarURLConnection = (JarURLConnection)url.openConnection();
                    JarFile jarFile = jarURLConnection.getJarFile();
                    fileNames = getClassNameByJar(jarFile,packagePath);
                }catch (java.io.IOException e){
                    throw new RuntimeException("open Package URL failed："+e.getMessage());
                }

            }else{
                throw new RuntimeException("file system not support! cannot load MsgProcessor！");
            }
        }
        return fileNames;
    }

    /**
     * 从项目文件获取某包下所有类
     * @param filePath 文件路径
     * @return 类的完整名称
     */
    private static List<String> getClassNameByFile(String filePath) {
        List<String> myClassName = new ArrayList<String>();
        File file = new File(filePath);
        File[] childFiles = file.listFiles();
        for (File childFile : childFiles) {
            if (childFile.isDirectory()) {
                myClassName.addAll(getClassNameByFile(childFile.getPath()));
            } else {
                String childFilePath = childFile.getPath();
                if (childFilePath.endsWith(".class")) {
                    childFilePath = childFilePath.substring(childFilePath.indexOf("\\classes") + 9, childFilePath.lastIndexOf("."));
                    childFilePath = childFilePath.replace("\\", ".");
                    myClassName.add(childFilePath);
                }
            }
        }

        return myClassName;
    }

    /**
     * 从jar获取某包下所有类
     * @return 类的完整名称
     */
    private static List<String> getClassNameByJar(JarFile jarFile ,String packagePath) {
        List<String> myClassName = new ArrayList<String>();
        try {
            Enumeration<JarEntry> entrys = jarFile.entries();
            while (entrys.hasMoreElements()) {
                JarEntry jarEntry = entrys.nextElement();
                String entryName = jarEntry.getName();
                //LOG.info("entrys jarfile:"+entryName);
                if (entryName.endsWith(".class")) {
                    entryName = entryName.replace("/", ".").substring(0, entryName.lastIndexOf("."));
                    myClassName.add(entryName);
                    //LOG.debug("Find Class :"+entryName);
                }
            }
        } catch (Exception e) {
            LOG.error("发生异常:"+e.getMessage());
            throw new RuntimeException("发生异常:"+e.getMessage());
        }
        return myClassName;
    }

}