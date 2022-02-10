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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;

abstract class AbstractClassSearcher implements ClassSearcher {

	private ClassLoader classLoader = Thread.currentThread()
			.getContextClassLoader();

	private Predicate<Class<?>> filter = new DefaultPredicate<Class<?>>();

	private Consumer<Throwable> exceptionHandler=e->{};
	
	/**
	 * @return the filter
	 */
	public Predicate<Class<?>> getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            the filter to set
	 */
	@Override
	public void setFilter(Predicate<Class<?>> filter) {
		this.filter = filter;
	}

	/**
	 * @return the classLoader
	 */
	public ClassLoader getClassLoader() {
		return classLoader;
	}

	/**
	 * @param classLoader
	 *            the classLoader to set
	 */
	@Override
	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	protected String fileNameToClassName(String name) {
		return name.substring(0, name.length() - ".class".length());
	}

	protected boolean isClassFile(String fileName) {
		return fileName.endsWith(".class");
	}

	public Consumer<Throwable> getExceptionHandler() {
		return exceptionHandler;
	}

	public void setExceptionHandler(Consumer<Throwable> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
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
}
