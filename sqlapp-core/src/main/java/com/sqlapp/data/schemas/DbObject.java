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

package com.sqlapp.data.schemas;

import java.util.Map;
import java.util.function.Consumer;

/**
 * DBオブジェクトIF
 * 
 * @author 竜夫
 * 
 * @param <T>
 */
public interface DbObject<T extends DbObject<? super T>> extends
		DbCommonObject<T> {

	/**
	 * オブジェクトが似ているか判定します
	 * 
	 * @param obj
	 */
	boolean like(Object obj);

	/**
	 * 指定したハンドラーでオブジェクトと近いかを判定します
	 * 
	 * @param obj
	 * @param equalsHandler
	 */
	boolean like(Object obj, EqualsHandler equalsHandler);

	/**
	 * 差分比較を行い結果のオブジェクトを取得します
	 * 
	 * @param obj
	 *            比較対象のオブジェクト
	 * @return 差分比較結果のオブジェクト
	 */
	DbObjectDifference diff(T obj);

	/**
	 * 差分比較を行い結果のオブジェクトを取得します
	 * 
	 * @param obj
	 *            比較対象のオブジェクト
	 * @param equalsHandler
	 *            比較に使用するEqualsHandler
	 * @return 差分比較結果のオブジェクト
	 */
	DbObjectDifference diff(T obj, EqualsHandler equalsHandler);

	/**
	 * マップでプロパティの値を取得します
	 * 
	 */
	Map<String, Object> toMap();
	/**
	 * このオブジェクトおよび全ての配下のDBオブジェクトにconsumerを適用します。
	 * @param consumer
	 */
	T applyAll(Consumer<DbObject<?>> consumer);

}
