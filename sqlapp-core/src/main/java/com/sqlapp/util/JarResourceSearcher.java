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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.jar.JarEntry;

import com.sqlapp.util.ResourceFinder.ResourceInfo;

/**
 * ファイルリソースを検索します
 * 
 * @author tatsuo satoh
 *
 */
public class JarResourceSearcher extends AbstractResourceSearcher {
	@Override
	public <T> List<ResourceInfo> search(String rootPackageName, URL url,
			boolean recursive) {

		AbstractJarHandler<ResourceInfo, ResourceInfo> jarHandler = new AbstractJarHandler<ResourceInfo, ResourceInfo>(
				this.getClassLoader(), this.getFilter()) {

			@Override
			protected void handleJarEntry(JarEntry jarEntry,
					String packageNameAsResourceName, boolean recursive,
					List<ResourceInfo> resources) {
				if (recursive) {
					if (jarEntry.getName()
							.startsWith(packageNameAsResourceName)
							&& isResourceFile(jarEntry.getName())) {
						URI uri;
						try {
							uri = this.getClassLoader()
									.getResource(jarEntry.getName()).toURI();
						} catch (URISyntaxException e) {
							throw new RuntimeException(e);
						}
						ResourceInfo resourceInfo = new ResourceInfo(uri,
								this.getClassLoader(),
								getPackage(jarEntry.getName()),
								getFileName(jarEntry.getName()));
						if (this.getFilter().test(resourceInfo)) {
							resources.add(resourceInfo);
						}
					}
				} else {
					if (!equalsPackage(jarEntry, packageNameAsResourceName)) {
						return;
					}
					if (isResourceFile(jarEntry.getName())) {
						URI uri;
						try {
							uri = this.getClassLoader()
									.getResource(jarEntry.getName()).toURI();
						} catch (URISyntaxException e) {
							throw new RuntimeException(e);
						}
						ResourceInfo resourceInfo = new ResourceInfo(uri,
								this.getClassLoader(),
								getPackage(jarEntry.getName()),
								getFileName(jarEntry.getName()));
						if (this.getFilter().test(resourceInfo)) {
							resources.add(resourceInfo);
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
}
