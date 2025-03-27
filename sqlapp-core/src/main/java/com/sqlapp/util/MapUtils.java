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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mapのユーティリティ
 */
public class MapUtils {

	/**
	 * 第1引数のマップに第2引数のマップをマージします
	 * 
	 * @param <S>             マップのキー
	 * @param <T>             マップの値
	 * @param obj             マージされるマップ
	 * @param other           マージするマップ
	 * @param relpaceFunction 値が両方とも存在する場合のどちらを優先するかのファンクション
	 * @param mapSupplier     元のマップの子供に新しくマップを追加するときに使用するMapのSupplier
	 */
	@SuppressWarnings("unchecked")
	public static <S, T> void merge(final Map<S, T> obj, final Map<S, T> other, KeyReplaceFunction relpaceFunction,
			@SuppressWarnings("rawtypes") Supplier<Map> mapSupplier) {
		for (Map.Entry<S, T> entry : other.entrySet()) {
			S key = entry.getKey();
			T value = entry.getValue();
			T originalValue = obj.get(key);
			if (originalValue == null) {
				if (value instanceof Map) {
					Map<S, T> child = mapSupplier.get();
					obj.put(key, (T) child);
					merge(child, (Map<S, T>) value, relpaceFunction, mapSupplier);
				} else {
					obj.put(key, value);
				}
			} else {
				if (value instanceof Map) {
					merge((Map<S, T>) originalValue, (Map<S, T>) value, relpaceFunction, mapSupplier);
				} else {
					obj.put(key, (T) relpaceFunction.apply(key, originalValue, value));
				}
			}
		}
	}

	/**
	 * 第1引数のマップに第2引数のマップをマージします
	 * 
	 * @param <S>             マップのキー
	 * @param <T>             マップの値
	 * @param obj             マージされるマップ
	 * @param other           マージするマップ
	 * @param relpaceFunction 値が両方とも存在する場合のどちらを優先するかのファンクション
	 */
	public static <S, T> void merge(final Map<S, T> obj, final Map<S, T> other, KeyReplaceFunction relpaceFunction) {
		merge(obj, other, relpaceFunction, () -> new LinkedHashMap<>());
	}

	/**
	 * 第1引数のマップに第2引数のマップをマージします
	 * 
	 * @param <S>   マップのキー
	 * @param <T>   マップの値
	 * @param obj   マージされるマップ
	 * @param other マージするマップ
	 */
	public static <S, T> void merge(final Map<S, T> obj, final Map<S, T> other) {
		merge(obj, other, (key, o1, o2) -> o2 != null ? o2 : o1);
	}

	/**
	 * マップマージ判定用のインタフェース
	 */
	@FunctionalInterface
	public static interface KeyReplaceFunction {
		Object apply(Object key, Object o1, Object o2);
	}
}
