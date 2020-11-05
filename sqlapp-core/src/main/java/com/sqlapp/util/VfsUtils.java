/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.util;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;

public class VfsUtils {
	private static Method GET_CONTENT_METHOD = null;

	private static Method GET_PHYSICAL_FILE_METHOD = null;

	private static Method GET_CHILDREN_METHOD = null;

	private static Method IS_FILE_METHOD = null;

	private static Method TO_URL = null;

	public static Object getContent(URLConnection obj) {
		if (GET_CONTENT_METHOD == null) {
			GET_CONTENT_METHOD = getMethod(obj, "getContent");
		}
		return invoke(GET_CONTENT_METHOD, obj);
	}

	public static File getPhisicalFile(Object obj) {
		if (GET_PHYSICAL_FILE_METHOD == null) {
			GET_PHYSICAL_FILE_METHOD = getMethod(obj, "getPhysicalFile");
		}
		return invoke(GET_PHYSICAL_FILE_METHOD, obj);
	}

	public static boolean isFile(Object obj) {
		if (IS_FILE_METHOD == null) {
			IS_FILE_METHOD = getMethod(obj, "isFile");
		}
		return invoke(IS_FILE_METHOD, obj);
	}

	public static Object getChildren(Object obj) {
		if (GET_CHILDREN_METHOD == null) {
			GET_CHILDREN_METHOD = getMethod(obj, "getChildren");
		}
		return invoke(GET_CHILDREN_METHOD, obj);
	}

	public static URL toURL(Object obj) {
		if (TO_URL == null) {
			TO_URL = getMethod(obj, "toURL");
		}
		return invoke(TO_URL, obj);
	}

	private static Method getMethod(Object obj, String name) {
		try {
			return obj.getClass().getMethod(name);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <S> S invoke(Method method, Object obj, Object... args) {
		try {
			return (S) method.invoke(obj, args);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
}
