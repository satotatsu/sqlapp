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

import java.util.List;
import java.util.Set;

/**
 * クラスの検索用クラス
 * 
 * @author tatsuo satoh
 * 
 */
public class ClassFinder extends AbstractClassFinder<Class<?>> {

	public ClassFinder() {
		super();
	}

	public ClassFinder(ClassLoader classLoader) {
		super(classLoader);
	}

	@Override
	protected void itialize() {
		addResourceSearcher(new VfsClassSearcher());
		addResourceSearcher(new JarClassSearcher());
		addResourceSearcher(new FileClassSearcher());
	}

	/**
	 * 指定されたパッケージからクラスファイルを取得します
	 * 
	 * @param packageName
	 *            パッケージ名
	 */
	@SuppressWarnings("unchecked")
	public <T> List<Class<? extends T>> find(String packageName) {
		ClassLoader classLoader = this.classLoader;
		List<?> list = findClasses(classLoader, packageName, false);
		return (List<Class<? extends T>>) list;
	}

	/**
	 * 指定されたパッケージから再帰的にクラスファイルを取得します
	 * 
	 * @param packageName
	 *            パッケージ名
	 */
	@SuppressWarnings("unchecked")
	public <T> List<Class<? extends T>> findRecursive(String packageName) {
		ClassLoader classLoader = this.classLoader;
		List<?> list = findClasses(classLoader, packageName, true);
		return ((List<Class<? extends T>>) list);
	}

	@Override
	protected void merge(List<Class<?>> classes, List<Class<?>> addClasses) {
		Set<String> names = CommonUtils.set();
		for (Class<?> clazz : classes) {
			names.add(clazz.getName());
		}
		for (Class<?> clazz : addClasses) {
			if (names.contains(clazz.getName())) {
				continue;
			}
			classes.add(clazz);
		}
	}

	@Override
	protected void initialize(Searcher<Class<?>> searcher) {
	}
}