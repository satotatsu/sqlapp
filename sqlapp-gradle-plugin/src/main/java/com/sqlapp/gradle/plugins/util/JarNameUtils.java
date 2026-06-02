package com.sqlapp.gradle.plugins.util;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JarNameUtils {
	private static final Pattern PATTERN = Pattern.compile("sqlapp-(core|command)(-[0-9.]*)?\\.jar");

	public static boolean isTarget(File f) {
		Matcher matcher = PATTERN.matcher(f.getName());
		if (matcher.matches()) {
			return true;
		}
		return false;
	}
}
