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
import java.util.function.Predicate;

public interface Searcher<T> {
	/**
	 * 指定したパッケージ配下のリソースを検索します。
	 * 
	 * @param packageName
	 * @param url
	 * @param recursive
	 * @return 指定したパッケージ配下のリソース
	 */
	<U> List<T> search(String packageName, URL url, boolean recursive);

	/**
	 * @param classLoader
	 *            the classLoader to set
	 */
	void setClassLoader(ClassLoader classLoader);

	/**
	 * @param filter
	 *            the filter to set
	 */
	void setFilter(Predicate<T> filter);

	/**
	 * サポートしているプロトコルを返します。
	 * 
	 */
	String[] supportProtocols();
}
