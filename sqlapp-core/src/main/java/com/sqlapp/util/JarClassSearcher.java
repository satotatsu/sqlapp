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

import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;

/**
 * ファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class JarClassSearcher extends AbstractClassSearcher {
	@Override
	public <T> List<Class<?>> search(String rootPackageName, URL url,
			boolean recursive) {
		AbstractJarHandler<Class<?>, Class<?>> jarHandler = new AbstractJarHandler<Class<?>, Class<?>>(
				this.getClassLoader(), this.getFilter()) {

			@Override
			protected void handleJarEntry(JarEntry jarEntry,
					String packageNameAsResourceName, boolean recursive,
					List<Class<?>> resources) throws ClassNotFoundException {
				if (recursive) {
					if (jarEntry.getName()
							.startsWith(packageNameAsResourceName)
							&& isClassFile(jarEntry.getName())) {
						try {
							@SuppressWarnings("unchecked")
							Class<T> clazz = (Class<T>) getClassLoader()
									.loadClass(
											resourceNameToClassName(jarEntry
													.getName()));
							if (this.getFilter().test(clazz)) {
								resources.add(clazz);
							}
						} catch (Exception e) {
							getExceptionHandler().accept(e);
						} catch (ClassFormatError e) {
							getExceptionHandler().accept(e);
						}
					}
				} else {
					if (equalsPackage(jarEntry, packageNameAsResourceName)
							&& isClassFile(jarEntry.getName())) {
						try {
							@SuppressWarnings("unchecked")
							Class<T> clazz = (Class<T>) getClassLoader()
									.loadClass(
											resourceNameToClassName(jarEntry
													.getName()));
							if (this.getFilter().test(clazz)) {
								resources.add(clazz);
							}
						} catch (Exception e) {
							getExceptionHandler().accept(e);
						} catch (ClassFormatError e) {
							getExceptionHandler().accept(e);
						}
					}
				}
			}

		};
		return jarHandler.search(rootPackageName, url, recursive);
	}

	private static final String[] PROTOCOLS = new String[] { "jar", "zip",
			"vfszip" };

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractResourceFinder#supportProtocols()
	 */
	@Override
	public String[] supportProtocols() {
		return PROTOCOLS;
	}

	private String resourceNameToClassName(String resourceName) {
		return fileNameToClassName(resourceName).replace('/', '.');
	}
}
