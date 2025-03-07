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
import java.net.URL;
import java.util.List;

/**
 * ファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class FileClassSearcher extends AbstractClassSearcher {

	@Override
	public <T> List<Class<?>> search(String packageName, URL url, boolean recursive) {
		final String path = FileUtils.toPath(url);
//		path = FileUtils.combinePath(path, packageName.replace(".", "/"));
		File file = new File(path);
		return searchInternal(packageName, file, recursive);
	}

	@SuppressWarnings("unchecked")
	protected <T> List<Class<? extends T>> searchInternal(String packageName, File file, boolean recursive) {
		List<Class<? extends T>> resources = CommonUtils.list();
		if (file.isFile()) {
			if (!isClassFile(file.getAbsolutePath())) {
				return resources;
			}
			Class<T> clazz;
			String name = packageName + "." + fileNameToClassName(file.getName()).replace("/", ".");
			try {
				clazz = (Class<T>) getClassLoader().loadClass(name);
				if (this.getFilter().test(clazz)) {
					resources.add(clazz);
				}
				return resources;
			} catch (Exception e) {
				clazz = ModuleHelper.getInstance().getClass(name);
				if (clazz != null && this.getFilter().test(clazz)) {
					resources.add(clazz);
				}
				getExceptionHandler().accept(e);
			} catch (ClassFormatError e) {
				getExceptionHandler().accept(e);
			}
		}
		String[] pathes = file.list();
		if (pathes == null) {
			return resources;
		}
		for (String path : pathes) {
			File entry = new File(file, path);
			if (entry.isFile()) {
				if (!isClassFile(entry.getName())) {
					continue;
				}
				String name = packageName + "." + fileNameToClassName(entry.getName()).replace("/", ".");
				Class<T> clazz;
				try {
					clazz = (Class<T>) getClassLoader().loadClass(name);
					if (this.getFilter().test(clazz)) {
						resources.add(clazz);
					}
				} catch (ClassNotFoundException e) {
					clazz = ModuleHelper.getInstance().getClass(name);
					if (clazz != null && this.getFilter().test(clazz)) {
						resources.add(clazz);
					}
					getExceptionHandler().accept(e);
				} catch (NoClassDefFoundError e) {
					getExceptionHandler().accept(e);
				} catch (ClassFormatError e) {
					getExceptionHandler().accept(e);
				}
			} else {
				if (recursive) {
					List<Class<? extends T>> cls = searchInternal(packageName + "." + entry.getName(), entry,
							recursive);
					resources.addAll(cls);
				}
			}
		}
		return resources;
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
