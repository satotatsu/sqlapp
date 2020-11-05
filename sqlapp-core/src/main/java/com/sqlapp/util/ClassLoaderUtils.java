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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ConcurrentMap;

public class ClassLoaderUtils {

	private static final ConcurrentMap<URL, ClassLoader> CACHE = CommonUtils
			.concurrentMap();

	/**
	 * 指定したクラスパスからクラスローダーを取得します
	 * 
	 * @param file
	 *            クラスパス
	 */
	public static ClassLoader getClassLoader(File file) {
		try {
			return getClassLoader(file.toURI().toURL(), Thread.currentThread()
					.getContextClassLoader());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 指定したクラスパスからクラスローダーを取得します
	 * 
	 * @param file
	 *            クラスパス
	 * @param parentClassLoader
	 */
	public static ClassLoader getClassLoader(File file,
			ClassLoader parentClassLoader) {
		try {
			return getClassLoader(file.toURI().toURL(), parentClassLoader);
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 指定したクラスパスからクラスローダーを取得します
	 * 
	 * @param url
	 *            クラスパス
	 */
	public static ClassLoader getClassLoader(URL url) {
		return getClassLoader(url, Thread.currentThread()
				.getContextClassLoader());
	}

	/**
	 * 指定したURLからクラスローダーを取得します
	 * 
	 * @param url
	 *            クラスパス
	 * @param parentClassLoader
	 */
	public static ClassLoader getClassLoader(URL url,
			ClassLoader parentClassLoader) {
		ClassLoader classLoader = CACHE.get(url);
		if (classLoader != null) {
			return classLoader;
		}
		classLoader = URLClassLoader.newInstance(new URL[] { url },
				parentClassLoader);
		ClassLoader org = CACHE.put(url, classLoader);
		return org != null ? org : classLoader;
	}

	/**
	 * 指定したURLからクラスローダーを取得します
	 * 
	 * @param url
	 * @param parentClassLoader
	 */
	public static ClassLoader reloadClassLoader(URL url,
			ClassLoader parentClassLoader) {
		CACHE.remove(url);
		return getClassLoader(url, parentClassLoader);
	}

}
