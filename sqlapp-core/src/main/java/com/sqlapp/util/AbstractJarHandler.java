/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractJarHandler<T, S> {
	private final Logger logger = LogManager.getLogger(this.getClass());
	private final ClassLoader classLoader;
	private final Predicate<S> filter;

	public AbstractJarHandler(ClassLoader classLoader, Predicate<S> filter) {
		this.classLoader = classLoader;
		this.filter = filter;
	}

	public List<T> search(String rootPackageName, URL url, boolean recursive) {
		List<T> classes = CommonUtils.list();
		URLConnection connection;
		try {
			connection = url.openConnection();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		JarURLConnection juconnection = null;
		if (connection instanceof JarURLConnection) {
			juconnection = (JarURLConnection) connection;
		}
		InputStream stream = null;
		try {
			String packageNameAsResourceName = packageNameToResourceName(rootPackageName);
			if (juconnection != null) {
				JarFile jarFile = null;
				try {
					jarFile = juconnection.getJarFile();
					Enumeration<JarEntry> enm = jarFile.entries();
					while (enm.hasMoreElements()) {
						JarEntry jarEntry = enm.nextElement();
						if (jarEntry.isDirectory()) {
							continue;
						}
						try {
							handleJarEntry(jarEntry, packageNameAsResourceName,
									recursive, classes);
						} catch (ClassNotFoundException e) {
							logger.warn(e.getMessage(), e);
						}
					}
				} finally {
					FileUtils.close(jarFile);
				}
			} else {
				stream = connection.getInputStream();
				JarEntry jarEntry = null;
				while ((jarEntry = getNextJarEntry(stream)) != null) {
					if (jarEntry.isDirectory()) {
						continue;
					}
					try {
						handleJarEntry(jarEntry, packageNameAsResourceName,
								recursive, classes);
					} catch (ClassNotFoundException e) {
						logger.warn(e.getMessage(), e);
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			FileUtils.close(stream);
		}
		return classes;
	}

	private String packageNameToResourceName(String packageName) {
		return packageName.replace('.', '/');
	}

	private Method getNextEntryMethod = null;

	protected JarEntry getNextJarEntry(Object obj) {
		if (getNextEntryMethod == null) {
			try {
				getNextEntryMethod = obj.getClass()
						.getMethod("getNextJarEntry");
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		try {
			return (JarEntry) getNextEntryMethod.invoke(obj);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	protected boolean equalsPackage(JarEntry jarEntry,
			String packageNameAsResourceName) {
		String name = jarEntry.getName();
		if (name.length() > packageNameAsResourceName.length() + 1) {
			if (!name.startsWith(packageNameAsResourceName)) {
				return false;
			}
			name = name.substring(packageNameAsResourceName.length() + 1);
			return !name.contains("/");
		} else {
			return false;
		}
	}

	/**
	 * @return the filter
	 */
	public Predicate<S> getFilter() {
		return filter;
	}

	/**
	 * @return the classLoader
	 */
	protected ClassLoader getClassLoader() {
		return classLoader;
	}

	protected abstract void handleJarEntry(JarEntry jarEntry,
			String packageNameAsResourceName, boolean recursive,
			List<T> resources) throws ClassNotFoundException;
}