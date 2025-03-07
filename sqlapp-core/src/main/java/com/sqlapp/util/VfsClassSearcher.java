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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * VFSファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class VfsClassSearcher extends AbstractClassSearcher {

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.ClassSearcher#search(java.lang.String, java.net.URL,
	 * boolean)
	 */
	@Override
	public <T> List<Class<?>> search(String packageName, URL url,
			boolean recursive) {
		List<Class<?>> classes = CommonUtils.list();
		URLConnection urlConnection;
		try {
			urlConnection = url.openConnection();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Object virtualFile = VfsUtils.getContent(urlConnection);
		if (VfsUtils.isFile(virtualFile)) {
			File file = VfsUtils.getPhisicalFile(virtualFile);
			if (!isClassFile(file.getAbsolutePath())) {
				return classes;
			}
			try {
				@SuppressWarnings("unchecked")
				Class<T> clazz = (Class<T>) this.getClassLoader()
						.loadClass(
								packageName + "."
										+ fileNameToClassName(file.getName()));
				if (this.getFilter().test(clazz)) {
					classes.add(clazz);
				}
				return classes;
			} catch (Exception e) {
				getExceptionHandler().accept(e);
			} catch (ClassFormatError e) {
				getExceptionHandler().accept(e);
			}
		}
		Object children = VfsUtils.getChildren(virtualFile);
		int length = Array.getLength(children);
		for (int i = 0; i < length; i++) {
			Object child = Array.get(children, i);
			File file = VfsUtils.getPhisicalFile(child);
			if (file.isFile()) {
				if (!isClassFile(file.getName())) {
					continue;
				}
				try {
					@SuppressWarnings("unchecked")
					Class<T> clazz = (Class<T>) this.getClassLoader()
							.loadClass(
									packageName
											+ "."
											+ fileNameToClassName(file
													.getName()));
					if (this.getFilter().test(clazz)) {
						classes.add(clazz);
					}
				} catch (Exception e) {
					getExceptionHandler().accept(e);
				} catch (ClassFormatError e) {
					getExceptionHandler().accept(e);
				}
			} else {
				if (recursive) {
					List<Class<?>> cls = search(
							packageName + "." + file.getName(),
							VfsUtils.toURL(child), recursive);
					classes.addAll(cls);
				}
			}
		}
		return classes;
	}

	private static final String[] PROTOCOLS = new String[] { "vfs" };

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.util.AbstractResourceFinder#supportProtocols()
	 */
	@Override
	public String[] supportProtocols() {
		return PROTOCOLS;
	}

}
