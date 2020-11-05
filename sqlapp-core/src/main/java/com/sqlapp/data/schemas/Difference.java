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

import java.util.function.BiPredicate;

import com.sqlapp.data.converter.Converters;

/**
 * 変更管理IF
 * 
 * @author tatsuo satoh
 * 
 */
public interface Difference<T> {
	/**
	 * 変更前のオブジェクトを取得します
	 * 
	 */
	T getOriginal();
	/**
	 * 変更前のオブジェクトを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	default <S> S getOriginal(Class<S> clazz) {
		Object obj=getOriginal();
		if (obj==null) {
			return null;
		}
		if (clazz.isInstance(obj)) {
			return (S)obj;
		}
		if (DbCommonObject.class.isInstance(obj)) {
			return null;
		}
		return Converters.getDefault().convertObject(obj, clazz);
	}

	/**
	 * 変更後のオブジェクトを取得します
	 * 
	 */
	T getTarget();
	/**
	 * 変更後のオブジェクトを取得します
	 * 
	 */
	@SuppressWarnings("unchecked")
	default <S> S getTarget(Class<S> clazz) {
		Object obj=getTarget();
		if (obj==null) {
			return null;
		}
		if (clazz.isInstance(obj)) {
			return (S)obj;
		}
		if (DbCommonObject.class.isInstance(obj)) {
			return null;
		}
		return Converters.getDefault().convertObject(obj, clazz);
	}
	/**
	 * 変更前のオブジェクトの親オブジェクトを取得します
	 * 
	 * @return the ownParent
	 */
	DbCommonObject<?> getOriginalParent();

	@SuppressWarnings("unchecked")
	default <S extends DbCommonObject<?>> S getOriginalParent(Class<S> clazz) {
		Object obj=getOriginalParent();
		if (obj==null) {
			return null;
		}
		if (clazz.isInstance(obj)) {
			return (S)obj;
		}
		if (DbCommonObject.class.isInstance(obj)) {
			return null;
		}
		return Converters.getDefault().convertObject(obj, clazz);
	}

	/**
	 * 変更後のオブジェクトの親オブジェクトを取得します
	 * 
	 * @return the targetParent
	 */
	DbCommonObject<?> getTargetParent();

	@SuppressWarnings("unchecked")
	default <S extends DbCommonObject<?>> S getTargetParent(Class<S> clazz) {
		Object obj=getTargetParent();
		if (obj==null) {
			return null;
		}
		if (clazz.isInstance(obj)) {
			return (S)obj;
		}
		if (DbCommonObject.class.isInstance(obj)) {
			return null;
		}
		return Converters.getDefault().convertObject(obj, clazz);
	}

	/**
	 * 親の変更オブジェクトを取得します
	 * 
	 */
	<S extends Difference<?>> S getParentDifference();

	/**
	 * ステートを取得します
	 * 
	 */
	State getState();

	/**
	 * DbObjectDifferenceに変換します
	 * 
	 */
	DbObjectDifference toDifference();

	/**
	 * DbObjectDifferenceCollectionに変換します
	 * 
	 */
	DbObjectDifferenceCollection toDifferenceCollection();
	
	void removeRecursive(BiPredicate<String, Difference<?>> predicate);

	Difference<?> reverse();
}
