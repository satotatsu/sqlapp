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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

/**
 * ファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class FileClassSearcher extends AbstractClassSearcher {

	@Override
	public <T> List<Class<?>> search(String packageName, URL url,
			boolean recursive) {
		String path=getPath(url);
		File file = new File(path);
		return searchInternal(packageName, file, recursive);
	}

	protected <T> List<Class<? extends T>> searchInternal(String packageName,
			File file, boolean recursive) {
		List<Class<? extends T>> classes = CommonUtils.list();
		if (file.isFile()) {
			if (!isClassFile(file.getAbsolutePath())) {
				return classes;
			}
			try {
				@SuppressWarnings("unchecked")
				Class<T> clazz = (Class<T>) getClassLoader()
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
		String[] pathes = file.list();
		if (pathes == null) {
			return classes;
		}
		for (String path : pathes) {
			File entry = new File(file, path);
			if (entry.isFile()) {
				if (!isClassFile(entry.getName())) {
					continue;
				}
				try {
					@SuppressWarnings("unchecked")
					Class<T> clazz = (Class<T>) getClassLoader().loadClass(
							packageName + "."
									+ fileNameToClassName(entry.getName()));
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
					List<Class<? extends T>> cls = searchInternal(packageName
							+ "." + entry.getName(), entry, recursive);
					classes.addAll(cls);
				}
			}
		}
		return classes;
	}

	private String getPath(URL url) {
		String file = url.getFile();
		try {
			return URLDecoder.decode(file, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	private static final String[] PROTOCOLS = new String[] { "file" };

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
