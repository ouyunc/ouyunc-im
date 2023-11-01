package com.ouyunc.im.utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 类扫描器
 * 参考hutool
 * @author looly
 * @since 4.6.9
 */
public class ClassScanner implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 包名
	 */
	private final String packageName;
	/**
	 * 包名，最后跟一个点，表示包名，避免在检查前缀时的歧义<br>
	 * 如果包名指定为空，不跟点
	 */
	private final String packageNameWithDot;
	/**
	 * 包路径，用于文件中对路径操作
	 */
	private final String packageDirName;
	/**
	 * 包路径，用于jar中对路径操作，在Linux下与packageDirName一致
	 */
	private final String packagePath;
	/**
	 * 过滤器
	 */
	private final Function<Class<?>, Boolean> classFilter;

	/**
	 * 类加载器
	 */
	private ClassLoader classLoader;

	/**
	 * 扫描结果集
	 */
	private final Set<Class<?>> classes = new HashSet<>();

	/**
	 * 忽略loadClass时的错误
	 */
	private boolean ignoreLoadError = false;

	/**
	 * 获取加载错误的类名列表
	 */
	private final Set<String> classesOfLoadError = new HashSet<>();


	/**
	 * 扫描指定包路径下所有指定类或接口的子类或实现类，不包括指定父类本身<br>
	 * 如果classpath下已经有类，不再扫描其他加载的jar或者类
	 *
	 * @param packageName 包路径
	 * @param superClass  父类或接口（不包括）
	 * @return 类集合
	 */
	public static Set<Class<?>> scanPackageBySuper(String packageName, Class<?> superClass) throws IOException {
		return new ClassScanner(packageName, clazz -> superClass.isAssignableFrom(clazz) && !superClass.equals(clazz)).scan();
	}



	/**
	 * 构造
	 *
	 * @param packageName 包名，所有包传入""或者null
	 */
	private ClassScanner(String packageName, Function<Class<?>, Boolean> classFilter) {
		if (packageName == null) {
			packageName = "";
		}
		this.packageName = packageName;
		this.classFilter = classFilter;
		this.packageNameWithDot = addSuffixIfNot(packageName, ".");
		this.packageDirName = packageName.replace('.', File.separatorChar);
		this.packagePath = packageName.replace('.', '/');
	}



	/**
	 * 扫描包路径下满足class过滤器条件的所有class文件<br>
	 * 此方法首先扫描指定包名下的资源目录，如果未扫描到，则扫描整个classpath中所有加载的类
	 *
	 * @return 类集合
	 */
	public Set<Class<?>> scan() throws IOException {
		return scan(false);
	}

	/**
	 * 扫描包路径下满足class过滤器条件的所有class文件
	 *
	 * @param forceScanJavaClassPaths 是否强制扫描其他位于classpath关联jar中的类
	 * @return 类集合
	 * @since 5.7.5
	 */
	public Set<Class<?>> scan(boolean forceScanJavaClassPaths) throws IOException {
		//多次扫描时,清理上次扫描历史
		this.classes.clear();
		this.classesOfLoadError.clear();
		Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(packagePath);
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			switch (url.getProtocol()) {
				case "file":
					scanFile(new File(URLDecoder.decode(url.getFile(), "UTF-8")), null);
					break;
				case "jar":
					scanJar(getJarFile(url));
					break;
			}
		}
		// classpath下未找到，则扫描其他jar包下的类
		if (forceScanJavaClassPaths || CollectionUtils.isEmpty(this.classes)) {
			scanJavaClassPaths();
		}
		return Collections.unmodifiableSet(this.classes);
	}

	public static JarFile getJarFile(URL url) throws IOException {
		try {
			JarURLConnection urlConnection = (JarURLConnection) url.openConnection();
			return urlConnection.getJarFile();
		} catch (IOException e) {
			throw new IOException(e);
		}
	}




	/**
	 * 扫描Java指定的ClassPath路径
	 */
	private void scanJavaClassPaths() throws IOException {
		final String[] javaClassPaths = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
		for (String classPath : javaClassPaths) {
			// bug修复，由于路径中空格和中文导致的Jar找不到
			classPath = URLDecoder.decode(classPath, "UTF-8");
			scanFile(new File(classPath), null);
		}
	}

	/**
	 * 扫描文件或目录中的类
	 *
	 * @param file    文件或目录
	 * @param rootDir 包名对应classpath绝对路径
	 */
	private void scanFile(File file, String rootDir) throws IOException {
		if (file.isFile()) {
			final String fileName = file.getAbsolutePath();
			if (fileName.endsWith(".class")) {
				final String className = fileName//
						// 8为classes长度，fileName.length() - 6为".class"的长度
						.substring(rootDir.length(), fileName.length() - 6)//
						.replace(File.separatorChar, '.');//
				//加入满足条件的类
				addIfAccept(className);
			} else if (fileName.endsWith(".jar")) {
				try {
					scanJar(new JarFile(file));
				} catch (IOException e) {
					throw new IOException(e);
				}
			}
		} else if (file.isDirectory()) {
			final File[] files = file.listFiles();
			if (null != files) {
				for (File subFile : files) {
					scanFile(subFile, (null == rootDir) ? subPathBeforePackage(file) : rootDir);
				}
			}
		}
	}

	/**
	 * 扫描jar包
	 *
	 * @param jar jar包
	 */
	private void scanJar(JarFile jar) {
		String name;
		Enumeration<JarEntry> jarEntryEnumeration = jar.entries();
		while (jarEntryEnumeration.hasMoreElements()) {
			JarEntry entry = jarEntryEnumeration.nextElement();
			name = removePrefix(entry.getName(), "/");
			if (StringUtils.isEmpty(packagePath) || name.startsWith(this.packagePath)) {
				if (name.endsWith(".class") && false == entry.isDirectory()) {
					final String className = name//
							.substring(0, name.length() - 6)//
							.replace('/', '.');//
					addIfAccept(loadClass(className));
				}
			}
		}
	}

	/**
	 * 去掉指定前缀
	 *
	 * @param str    字符串
	 * @param prefix 前缀
	 * @return 切掉后的字符串，若前缀不是 preffix， 返回原字符串
	 */
	public static String removePrefix(CharSequence str, CharSequence prefix) {
		if (StringUtils.isEmpty(str) || StringUtils.isEmpty(prefix)) {
			return null == str ? null : str.toString();
		}
		final String str2 = str.toString();
		if (str2.startsWith(prefix.toString())) {
			if (StringUtils.isEmpty(str2)) {
				return null;
			}
			return sub(str2, prefix.length(), str2.length());// 截取后半段
		}
		return str2;
	}
	/**
	 * 改进JDK subString<br>
	 * index从0开始计算，最后一个字符为-1<br>
	 * 如果from和to位置一样，返回 "" <br>
	 * 如果from或to为负数，则按照length从后向前数位置，如果绝对值大于字符串长度，则from归到0，to归到length<br>
	 * 如果经过修正的index中from大于to，则互换from和to example: <br>
	 * abcdefgh 2 3 =》 c <br>
	 * abcdefgh 2 -3 =》 cde <br>
	 *
	 * @param str              String
	 * @param fromIndexInclude 开始的index（包括）
	 * @param toIndexExclude   结束的index（不包括）
	 * @return 字串
	 */
	public static String sub(CharSequence str, int fromIndexInclude, int toIndexExclude) {
		if (StringUtils.isEmpty(str)) {
			return null == str ? null : str.toString();
		}
		int len = str.length();

		if (fromIndexInclude < 0) {
			fromIndexInclude = len + fromIndexInclude;
			if (fromIndexInclude < 0) {
				fromIndexInclude = 0;
			}
		} else if (fromIndexInclude > len) {
			fromIndexInclude = len;
		}

		if (toIndexExclude < 0) {
			toIndexExclude = len + toIndexExclude;
			if (toIndexExclude < 0) {
				toIndexExclude = len;
			}
		} else if (toIndexExclude > len) {
			toIndexExclude = len;
		}

		if (toIndexExclude < fromIndexInclude) {
			int tmp = fromIndexInclude;
			fromIndexInclude = toIndexExclude;
			toIndexExclude = tmp;
		}

		if (fromIndexInclude == toIndexExclude) {
			return "";
		}

		return str.toString().substring(fromIndexInclude, toIndexExclude);
	}
	/**
	 * 加载类
	 *
	 * @param className 类名
	 * @return 加载的类
	 */
	protected Class<?> loadClass(String className) {
		ClassLoader loader = this.classLoader;
		if (null == loader) {
			loader = Thread.currentThread().getContextClassLoader();
			this.classLoader = loader;
		}

		Class<?> clazz = null;
		try {
			// 加载类时不初始化静态代码块
			clazz = Class.forName(className,false, Thread.currentThread().getContextClassLoader());
		} catch (NoClassDefFoundError | ClassNotFoundException e) {
			// 由于依赖库导致的类无法加载，直接跳过此类
			classesOfLoadError.add(className);
		} catch (UnsupportedClassVersionError e) {
			// 版本导致的不兼容的类，跳过
			classesOfLoadError.add(className);
		} catch (Throwable e) {
			if (false == this.ignoreLoadError) {
				throw new RuntimeException(e);
			} else {
				classesOfLoadError.add(className);
			}
		}
		return clazz;
	}

	/**
	 * 通过过滤器，是否满足接受此类的条件
	 *
	 * @param className 类名
	 */
	private void addIfAccept(String className) {
		if (StringUtils.isBlank(className)) {
			return;
		}
		int classLen = className.length();
		int packageLen = this.packageName.length();
		if (classLen == packageLen) {
			//类名和包名长度一致，用户可能传入的包名是类名
			if (className.equals(this.packageName)) {
				addIfAccept(loadClass(className));
			}
		} else if (classLen > packageLen) {
			//检查类名是否以指定包名为前缀，包名后加.（避免类似于cn.hutool.A和cn.hutool.ATest这类类名引起的歧义）
			if (".".equals(this.packageNameWithDot) || className.startsWith(this.packageNameWithDot)) {
				addIfAccept(loadClass(className));
			}
		}
	}

	/**
	 * 通过过滤器，是否满足接受此类的条件
	 *
	 * @param clazz 类
	 */
	private void addIfAccept(Class<?> clazz) {
		if (null != clazz) {
			Function<Class<?>, Boolean> classFilter = this.classFilter;
			if (classFilter == null || classFilter.apply(clazz)) {
				this.classes.add(clazz);
			}
		}
	}

	/**
	 * 截取文件绝对路径中包名之前的部分
	 *
	 * @param file 文件
	 * @return 包名之前的部分
	 */
	private String subPathBeforePackage(File file) {
		String filePath = file.getAbsolutePath();
		if (StringUtils.isNotEmpty(this.packageDirName)) {
			filePath = subBefore(filePath, this.packageDirName, true);
		}
		return addSuffixIfNot(filePath, File.separator);
	}
	/**
	 * 如果给定字符串不是以suffix结尾的，在尾部补充 suffix
	 *
	 * @param str    字符串
	 * @param suffix 后缀
	 * @return 补充后的字符串
	 * @see #appendIfMissing(CharSequence, CharSequence, CharSequence...)
	 */
	public static String addSuffixIfNot(CharSequence str, CharSequence suffix) {
		return appendIfMissing(str, suffix, suffix);
	}
	/**
	 * 如果给定字符串不是以给定的一个或多个字符串为结尾，则在尾部添加结尾字符串<br>
	 * 不忽略大小写
	 *
	 * @param str      被检查的字符串
	 * @param suffix   需要添加到结尾的字符串
	 * @param suffixes 需要额外检查的结尾字符串，如果以这些中的一个为结尾，则不再添加
	 * @return 如果已经结尾，返回原字符串，否则返回添加结尾的字符串
	 * @since 3.0.7
	 */
	public static String appendIfMissing(CharSequence str, CharSequence suffix, CharSequence... suffixes) {
		return appendIfMissing(str, suffix, false, suffixes);
	}
	/**
	 * 如果给定字符串不是以给定的一个或多个字符串为结尾，则在尾部添加结尾字符串
	 *
	 * @param str          被检查的字符串
	 * @param suffix       需要添加到结尾的字符串，不参与检查匹配
	 * @param ignoreCase   检查结尾时是否忽略大小写
	 * @param testSuffixes 需要额外检查的结尾字符串，如果以这些中的一个为结尾，则不再添加
	 * @return 如果已经结尾，返回原字符串，否则返回添加结尾的字符串
	 * @since 3.0.7
	 */
	public static String appendIfMissing(CharSequence str, CharSequence suffix, boolean ignoreCase, CharSequence... testSuffixes) {
		if (str == null || StringUtils.isEmpty(suffix) || endWith(str, suffix, ignoreCase)) {
			return null == str ? null : str.toString();
		}
		if (ArrayUtils.isNotEmpty(testSuffixes)) {
			for (final CharSequence testSuffix : testSuffixes) {
				if (endWith(str, testSuffix, ignoreCase)) {
					return str.toString();
				}
			}
		}
		return str.toString().concat(suffix.toString());
	}
	/**
	 * 是否以指定字符串结尾<br>
	 * 如果给定的字符串和开头字符串都为null则返回true，否则任意一个值为null返回false
	 *
	 * @param str        被监测字符串
	 * @param suffix     结尾字符串
	 * @param ignoreCase 是否忽略大小写
	 * @return 是否以指定字符串结尾
	 */
	public static boolean endWith(CharSequence str, CharSequence suffix, boolean ignoreCase) {
		return endWith(str, suffix, ignoreCase, false);
	}


	/**
	 * 是否以指定字符串结尾<br>
	 * 如果给定的字符串和开头字符串都为null则返回true，否则任意一个值为null返回false
	 *
	 * @param str          被监测字符串
	 * @param suffix       结尾字符串
	 * @param ignoreCase   是否忽略大小写
	 * @param ignoreEquals 是否忽略字符串相等的情况
	 * @return 是否以指定字符串结尾
	 * @since 5.8.0
	 */
	public static boolean endWith(CharSequence str, CharSequence suffix, boolean ignoreCase, boolean ignoreEquals) {
		if (null == str || null == suffix) {
			if (ignoreEquals) {
				return false;
			}
			return null == str && null == suffix;
		}

		final int strOffset = str.length() - suffix.length();
		boolean isEndWith = str.toString()
				.regionMatches(ignoreCase, strOffset, suffix.toString(), 0, suffix.length());

		if (isEndWith) {
			return (false == ignoreEquals) || (false == equals(str, suffix, ignoreCase));
		}
		return false;
	}

	/**
	 * 比较两个字符串是否相等，规则如下
	 * <ul>
	 *     <li>str1和str2都为{@code null}</li>
	 *     <li>忽略大小写使用{@link String#equalsIgnoreCase(String)}判断相等</li>
	 *     <li>不忽略大小写使用{@link String#contentEquals(CharSequence)}判断相等</li>
	 * </ul>
	 *
	 * @param str1       要比较的字符串1
	 * @param str2       要比较的字符串2
	 * @param ignoreCase 是否忽略大小写
	 * @return 如果两个字符串相同，或者都是{@code null}，则返回{@code true}
	 * @since 3.2.0
	 */
	public static boolean equals(CharSequence str1, CharSequence str2, boolean ignoreCase) {
		if (null == str1) {
			// 只有两个都为null才判断相等
			return str2 == null;
		}
		if (null == str2) {
			// 字符串2空，字符串1非空，直接false
			return false;
		}

		if (ignoreCase) {
			return str1.toString().equalsIgnoreCase(str2.toString());
		} else {
			return str1.toString().contentEquals(str2);
		}
	}
	// --------------------------------------------------------------------------------------------------- Private method end
	/**
	 * 截取分隔字符串之前的字符串，不包括分隔字符串<br>
	 * 如果给定的字符串为空串（null或""）或者分隔字符串为null，返回原字符串<br>
	 * 如果分隔字符串为空串""，则返回空串，如果分隔字符串未找到，返回原字符串，举例如下：
	 *
	 * <pre>
	 * StrUtil.subBefore(null, *, false)      = null
	 * StrUtil.subBefore("", *, false)        = ""
	 * StrUtil.subBefore("abc", "a", false)   = ""
	 * StrUtil.subBefore("abcba", "b", false) = "a"
	 * StrUtil.subBefore("abc", "c", false)   = "ab"
	 * StrUtil.subBefore("abc", "d", false)   = "abc"
	 * StrUtil.subBefore("abc", "", false)    = ""
	 * StrUtil.subBefore("abc", null, false)  = "abc"
	 * </pre>
	 *
	 * @param string          被查找的字符串
	 * @param separator       分隔字符串（不包括）
	 * @param isLastSeparator 是否查找最后一个分隔字符串（多次出现分隔字符串时选取最后一个），true为选取最后一个
	 * @return 切割后的字符串
	 * @since 3.1.1
	 */
	public static String subBefore(CharSequence string, CharSequence separator, boolean isLastSeparator) {
		if (StringUtils.isEmpty(string) || separator == null) {
			return null == string ? null : string.toString();
		}

		final String str = string.toString();
		final String sep = separator.toString();
		if (sep.isEmpty()) {
			return "";
		}
		final int pos = isLastSeparator ? str.lastIndexOf(sep) : str.indexOf(sep);
		if (-1 == pos) {
			return str;
		}
		if (0 == pos) {
			return "";
		}
		return str.substring(0, pos);
	}
}
