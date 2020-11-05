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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * クラスの検索用抽象クラス
 * 
 * @author tatsuo satoh
 * 
 */
abstract class AbstractClassFinder<T> {
	protected ClassLoader classLoader;

	private Predicate<T> filter = new DefaultPredicate<T>();

	Map<String, Searcher<T>> resourceSearchers = CommonUtils.map();

	public AbstractClassFinder() {
		classLoader = Thread.currentThread().getContextClassLoader();
		itialize();
	}

	public AbstractClassFinder(ClassLoader classLoader) {
		this.classLoader = classLoader;
		itialize();
	}

	protected abstract void itialize();

	public void addResourceSearcher(Searcher<T> resourceFinder) {
		for (String protocol : resourceFinder.supportProtocols()) {
			addResourceSearcher(protocol, resourceFinder);
		}
	}

	public void addResourceSearcher(String protocol, Searcher<T> resourceFinder) {
		resourceSearchers.put(protocol, resourceFinder);
	}

	/**
	 * @return the filter
	 */
	public Predicate<T> getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            the filter to set
	 */
	public void setFilter(Predicate<T> filter) {
		this.filter = filter;
	}

	private String packageNameToResourceName(String packageName) {
		return packageName.replace('.', '/');
	}

	/**
	 * 指定されたパッケージからクラスファイルを取得します
	 * 
	 * @param packageName
	 *            パッケージ名
	 * @param recursive
	 *            配下のパッケージを再帰的に探すフラグ
	 */
	protected List<T> findClasses(final ClassLoader classLoader,
			String packageName, final boolean recursive) {
		final List<T> classes = CommonUtils.list();
		if (CommonUtils.isEmpty(packageName)) {
			Method urlMethod = getGetURLsMethod(classLoader.getClass());
			if (urlMethod != null) {
				try {
					Object obj = urlMethod.invoke(classLoader);
					AbstractIterator<URL> itr = new AbstractIterator<URL>() {
						@Override
						protected void handle(URL obj, int index)
								throws Exception {
							List<T> cls = findClasses(classLoader, "",
									recursive, obj);
							merge(classes, cls);
						}
					};
					itr.execute(obj);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			if (recursive) {
				Method method = getPackagesMethod(classLoader.getClass());
				if (method == null) {
					return classes;
				}
				try {
					Package[] packages = (Package[]) method.invoke(classLoader);
					Set<String> roots = getRootPackages(packages);
					for (String root : roots) {
						String resourceName = packageNameToResourceName(root);
						Enumeration<URL> enm;
						try {
							enm = classLoader.getResources(resourceName);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						while (enm.hasMoreElements()) {
							URL url = enm.nextElement();
							List<T> cls = findClasses(classLoader, root,
									recursive, url);
							merge(classes, cls);
						}
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		} else {
			String resourceName = packageNameToResourceName(packageName);
			Enumeration<URL> enm;
			try {
				enm = classLoader.getResources(resourceName);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			while (enm.hasMoreElements()) {
				URL url = enm.nextElement();
				List<T> cls = findClasses(classLoader, packageName, recursive,
						url);
				merge(classes, cls);
			}
		}
		return classes;
	}

	private Set<String> getRootPackages(Package[] packages) {
		Set<String> result = CommonUtils.linkedSet();
		for (Package pack : packages) {
			String[] splits = pack.getName().split("\\.");
			result.add(CommonUtils.first(splits));
		}
		return result;
	}

	private Method getPackagesMethod(Class<?> clazz) {
		if (getPackagesMethod != null) {
			return this.getPackagesMethod;
		}
		if (clazz == null) {
			return null;
		}
		Method[] methods = clazz.getDeclaredMethods();
		for (Method method : methods) {
			if (!"getPackages".equalsIgnoreCase(method.getName())) {
				continue;
			}
			if (method.getParameterCount() != 0) {
				continue;
			}
			method.setAccessible(true);
			getPackagesMethod = method;
			return method;
		}
		return getPackagesMethod(clazz.getSuperclass());
	}

	private Method getPackagesMethod = null;

	private Method getGetURLsMethod(Class<?> clazz) {
		if (getURLsMethod != null) {
			return this.getURLsMethod;
		}
		if (clazz == null) {
			return null;
		}
		Method[] methods = clazz.getMethods();
		for (Method method : methods) {
			if (!"getURLs".equalsIgnoreCase(method.getName())) {
				continue;
			}
			if (method.getParameterCount() != 0) {
				continue;
			}
			method.setAccessible(true);
			getURLsMethod = method;
			return method;
		}
		return null;
	}

	private Method getURLsMethod = null;

	protected abstract void merge(List<T> classes, List<T> addClasses);

	protected <U> List<T> findClasses(ClassLoader classLoader,
			String packageName, boolean recursive, URL url) {
		if (url == null) {
			return CommonUtils.list();
		}
		String protocol = url.getProtocol();
		Searcher<T> searcher = resourceSearchers.get(protocol);
		if ("file".equals(url.getProtocol())) {
			String extension = FileUtils.getExtension(url.getFile());
			Searcher<T> jarSearcher = resourceSearchers.get("jar");
			if (("jar".equals(extension) || "zip".equals(extension))
					&& jarSearcher != null) {
				try {
					url = new URL("jar:" + url.toString() + "!/");
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
				searcher = jarSearcher;
			}
		}
		if (searcher != null) {
			searcher.setClassLoader(classLoader);
			searcher.setFilter(filter);
			initialize(searcher);
			return searcher.search(packageName, url, recursive);
		}
		throw new IllegalArgumentException("Unsupported Class Load Protodol["
				+ protocol + "]");
	}

	protected abstract void initialize(Searcher<T> searcher);
}