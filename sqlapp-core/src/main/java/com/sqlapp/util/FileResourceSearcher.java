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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;

import com.sqlapp.util.ResourceFinder.ResourceInfo;

/**
 * ファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class FileResourceSearcher extends AbstractResourceSearcher {

	@Override
	public <T> List<ResourceInfo> search(String packageName, URL url, boolean recursive) {
		File file = new File(getPath(url));
		return searchInternal(packageName, file, recursive);
	}

	protected <T> List<ResourceInfo> searchInternal(String packageName, File file, boolean recursive) {
		List<ResourceInfo> resources = CommonUtils.list();
		if (file.isFile()) {
			if (!isResourceFile(file.getAbsolutePath())) {
				return resources;
			}
			ResourceInfo resourceInfo = new ResourceInfo(file.toURI(), this.getClassLoader(), packageName,
					file.getName());
			if (this.getFilter().test(resourceInfo)) {
				resources.add(resourceInfo);
			} else {
				Module module = ModuleHelper.getInstance().getModuleByPackage(packageName);
				if (module != null) {
					resourceInfo = new ResourceInfo(module, packageName, file.getName());
					if (resourceInfo.exists() && this.getFilter().test(resourceInfo)) {
						resources.add(resourceInfo);
					}
				}
			}
			return resources;
		}
		String[] pathes = file.list();
		if (pathes == null) {
			return resources;
		}
		for (String path : pathes) {
			File entry = new File(file, path);
			if (entry.isFile()) {
				if (!isResourceFile(entry.getName())) {
					continue;
				}
				ResourceInfo resourceInfo = new ResourceInfo(entry.toURI(), this.getClassLoader(), packageName,
						entry.getName());
				if (this.getFilter().test(resourceInfo)) {
					resources.add(resourceInfo);
				} else {
					Module module = ModuleHelper.getInstance().getModuleByPackage(packageName);
					if (module != null) {
						resourceInfo = new ResourceInfo(module, packageName, entry.getName());
						if (resourceInfo.exists() && this.getFilter().test(resourceInfo)) {
							resources.add(resourceInfo);
						}
					}
				}
			} else {
				if (recursive) {
					resources.addAll(searchInternal(packageName + "." + entry.getName(), entry, recursive));
				}
			}
		}
		return resources;
	}

	private String getPath(URL url) {
		String file = url.getFile();
		try {
			return URLDecoder.decode(file, "ISO8859_1");
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
