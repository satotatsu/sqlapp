/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

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
