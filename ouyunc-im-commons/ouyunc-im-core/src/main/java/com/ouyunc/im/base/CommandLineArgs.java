package com.ouyunc.im.base;

import java.util.*;

/**
 * 命令行的参数封装（参考springboot源码命令行解析）
 */
public class CommandLineArgs {

	private final Map<String, Object> optionArgs = new HashMap<>();

	private final Set<String> nonOptionArgs = new HashSet<>();


	/**
	 * 添加参数
	 * @param optionName
	 * @param optionValue
	 */
	public void addOptionArg(String optionName, String optionValue) {
		Object oldOptionValues = this.optionArgs.get(optionName);
		if (oldOptionValues == null) {
			this.optionArgs.put(optionName, optionValue);
		}else {
			if (oldOptionValues instanceof Set) {
				// 如果已经是set集合则直接加入数据
				((Set<String>)(oldOptionValues)).add(optionValue);
				this.optionArgs.put(optionName, oldOptionValues);
				return;
			}
			HashSet<Object> objects = new HashSet<>();
			objects.add(oldOptionValues);
			objects.add(optionValue);
			this.optionArgs.put(optionName, objects);
		}
	}

	/**
	 * Return the set of all option arguments present on the command line.
	 */
	public Set<String> getOptionNames() {
		return Collections.unmodifiableSet(this.optionArgs.keySet());
	}

	/**
	 * Return whether the option with the given name was present on the command line.
	 */
	public boolean containsOption(String optionName) {
		return this.optionArgs.containsKey(optionName);
	}

	/**
	 * Return the list of values associated with the given option. {@code null} signifies
	 * that the option was not present; empty list signifies that no values were associated
	 * with this option.
	 */
	public Object getOptionValues(String optionName) {
		return this.optionArgs.get(optionName);
	}

	/**
	 * Add the given value to the list of non-option arguments.
	 */
	public void addNonOptionArg(String value) {
		this.nonOptionArgs.add(value);
	}

	/**
	 * Return the list of non-option arguments specified on the command line.
	 */
	public Set<String> getNonOptionArgs() {
		return Collections.unmodifiableSet(this.nonOptionArgs);
	}

}
