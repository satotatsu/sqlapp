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
package com.sqlapp.data.schemas;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * DBオブジェクトコレクションIF
 * 
 * @param <T>
 */
public interface DbObjectCollection<T extends DbObject<?>> extends
		DbCommonObject<T>, List<T>, Sortable<T> {
	/**
	 * コレクションのクラスを取得します
	 * 
	 */
	Class<T> getType();

	/**
	 * 指定したオブジェクトに最も近いオブジェクトを取得します
	 * 
	 * @param obj
	 */
	T find(T obj);

	/**
	 * 指定したオブジェクトに最も近いオブジェクトを取得します
	 * 
	 * @param obj
	 */
	T find(Object obj);

	/**
	 * 差分比較を行い結果のオブジェクトを取得します
	 * 
	 * @return 差分比較結果のオブジェクト
	 */
	DbObjectDifferenceCollection diff(DbObjectCollection<T> obj);

	/**
	 * 差分比較を行い結果のオブジェクトを取得します
	 * 
	 * @param obj
	 *            比較対象のオブジェクト
	 * @param equalsHandler
	 *            比較に使用するEqualsHandler
	 * @return 差分比較結果のオブジェクト
	 */
	DbObjectDifferenceCollection diff(DbObjectCollection<T> obj,
			EqualsHandler equalsHandler);
	
	/**
	 * ソートを実行します
	 */
	void sort();

	/**
	 * Comparatorを渡して強制的にソートを実行します
	 */
	default void sort(Comparator<? super T> comparator){
		Sortable.super.sort(comparator);
	}
	
	/**
	 * 全ての配下のDBオブジェクトにconsumerを適用します。
	 * @param consumer
	 */
	void applyAll(Consumer<DbObject<?>> consumer);

}
