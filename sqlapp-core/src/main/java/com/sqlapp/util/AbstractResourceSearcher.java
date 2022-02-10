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

import java.util.Set;
import java.util.function.Predicate;

import com.sqlapp.util.ResourceFinder.ResourceInfo;

abstract class AbstractResourceSearcher implements ResourceSearcher {

	private ClassLoader classLoader = Thread.currentThread()
			.getContextClassLoader();

	private Predicate<ResourceInfo> filter = new DefaultPredicate<ResourceInfo>();

	private String[] extensions = new String[] { "properties" };

	private Set<String> extensionSets = CommonUtils.set(extensions);

	/**
	 * @return the extensionSets
	 */
	public Set<String> getExtensionSets() {
		return extensionSets;
	}

	/**
	 * @param extensionSets
	 *            the extensionSets to set
	 */
	@Override
	public void setExtensionSets(Set<String> extensionSets) {
		this.extensionSets = extensionSets;
	}

	/**
	 * @return the filter
	 */
	public Predicate<ResourceInfo> getFilter() {
		return filter;
	}

	/**
	 * @param filter
	 *            the filter to set
	 */
	@Override
	public void setFilter(Predicate<ResourceInfo> filter) {
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

	protected boolean isResourceFile(String fileName) {
		if (CommonUtils.isEmpty(fileName)) {
			return false;
		}
		if (fileName.endsWith("/")) {
			return false;
		}
		String ext = FileUtils.getExtension(fileName);
		if (CommonUtils.isEmpty(this.getExtensionSets())) {
			return true;
		}
		return this.getExtensionSets().contains(ext);
	}

	protected String getFileName(String name) {
		int pos = name.lastIndexOf('/');
		if (pos > 0) {
			return name.substring(pos + 1);
		}
		return name;
	}

	protected String getPackage(String name) {
		int pos = name.lastIndexOf('/');
		if (pos > 0) {
			return name.substring(0, pos).replace("/", ".");
		}
		return name;
	}
}
