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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

public class SimpleURLClassLoader extends URLClassLoader {

	public SimpleURLClassLoader(URL... urls) {
		super(urls);
	}

	public SimpleURLClassLoader(File... files) {
		super(toURL(files));
	}

	static URL[] toURL(File... files) {
		List<URL> urls = CommonUtils.list();
		for (File file : files) {
			try {
				urls.add(file.toURI().toURL());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return urls.toArray(new URL[0]);
	}

	@Override
	public Enumeration<URL> getResources(final String name) {
		@SuppressWarnings("unchecked")
		Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[1];
		try {
			tmp[0] = findResources(name);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return new CompoundEnumeration<URL>(tmp);
	}

	@Override
	public URL findResource(final String name) {
		return super.findResource(name);
	}

	public static class CompoundEnumeration<E> implements Enumeration<E> {
		private Enumeration<E>[] enums;
		private int index = 0;

		public CompoundEnumeration(Enumeration<E>[] paramArrayOfEnumeration) {
			this.enums = paramArrayOfEnumeration;
		}

		private boolean next() {
			while (this.index < this.enums.length) {
				if ((this.enums[this.index] != null)
						&& (this.enums[this.index].hasMoreElements())) {
					return true;
				}
				this.index += 1;
			}
			return false;
		}

		public boolean hasMoreElements() {
			return next();
		}

		public E nextElement() {
			if (!(next())) {
				throw new NoSuchElementException();
			}
			return this.enums[this.index].nextElement();
		}
	}

}
