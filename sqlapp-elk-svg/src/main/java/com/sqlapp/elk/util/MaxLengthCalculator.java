package com.sqlapp.elk.util;

import com.sqlapp.util.CommonUtils;

public class MaxLengthCalculator {
	private int current;

	public MaxLengthCalculator(int min) {
		this.current = min;
	}

	public MaxLengthCalculator() {
		this(0);
	}

	public void add(String value) {
		if (CommonUtils.isEmpty(value)) {
			return;
		}
		if (value.length() > current) {
			current = value.length();
		}
	}

	public void add(String... args) {
		if (args == null) {
			return;
		}
		for (String arg : args) {
			add(arg);
		}
	}

	public int calc() {
		return current;
	}
}
