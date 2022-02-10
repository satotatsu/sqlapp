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
import java.util.function.BooleanSupplier;

/**
 * equalsの判定を行うためのインタフェース
 * 
 * @author satoh
 * 
 */
public class EqualsHandler implements Cloneable{

	private static final EqualsHandler instance=new EqualsHandler();
	
	public static EqualsHandler getInstance(){
		return instance;
	}
	
	private BiPredicate<Object,Object> referenceEqualsPredicate=(object1, object2)->{
		if (object1 == object2) {
			return true;
		}
		if (object2 == null) {
			return false;
		}
		return false;
	};

	private EqualsPredicate valueEqualsPredicate=(propertyName, eq, object1,
			object2, value1, value2)->{
		return eq;
	};

	private BiPredicate<Object,Object> equalsLastPredicate=(object1, object2)->{
		return true;
	};
	
	/**
	 * @return the referenceEqualsPredicate
	 */
	public BiPredicate<Object, Object> getReferenceEqualsPredicate() {
		return referenceEqualsPredicate;
	}

	/**
	 * @param referenceEqualsPredicate the referenceEqualsPredicate to set
	 */
	public void setReferenceEqualsPredicate(BiPredicate<Object, Object> referenceEqualsPredicate) {
		this.referenceEqualsPredicate = referenceEqualsPredicate;
	}

	/**
	 * @return the valueEqualsPredicate
	 */
	public EqualsPredicate getValueEqualsPredicate() {
		return valueEqualsPredicate;
	}

	/**
	 * @param valueEqualsPredicate the valueEqualsPredicate to set
	 */
	public void setValueEqualsPredicate(EqualsPredicate valueEqualsPredicate) {
		this.valueEqualsPredicate = valueEqualsPredicate;
	}

	/**
	 * @return the equalsLastPredicate
	 */
	public BiPredicate<Object, Object> getEqualsLastPredicate() {
		return equalsLastPredicate;
	}

	/**
	 * @param equalsLastPredicate the equalsLastPredicate to set
	 */
	public void setEqualsLastPredicate(BiPredicate<Object, Object> equalsLastPredicate) {
		this.equalsLastPredicate = equalsLastPredicate;
	}

	@FunctionalInterface
	public static interface EqualsPredicate{
		boolean contextEquals(String propertyName, boolean eq, Object object1,
				Object object2, Object value1, Object value2);
	}

	/**
	 * 参照の比較結果を返します
	 * 
	 * @param object1
	 *            比較対象オブジェクト1
	 * @param object2
	 *            比較対象オブジェクト2
	 */
	protected boolean referenceEquals(Object object1, Object object2){
		return getReferenceEqualsPredicate().test(object1, object2);
	}
	
	/**
	 * プロパティの値の比較を実施します。
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param object1
	 *            比較対象オブジェクト1
	 * @param object2
	 *            比較対象オブジェクト2
	 * @param value1
	 *            比較対象値1
	 * @param value2
	 *            比較対象値2
	 */
	protected boolean valueEquals(String propertyName, Object object1, Object object2,
			Object value1, Object value2, BooleanSupplier p){
		if (value1 instanceof DbCommonObject) {
			if (((DbCommonObject<?>) value1).equals(value2, this)) {
				return true;
			} else{
				return false;
			}
		}
		return getValueEqualsPredicate().contextEquals(propertyName, p.getAsBoolean(), object1, object2, value1, value2);
	}

	/**
	 * 最終的な比較結果を返します
	 * 
	 * @param object1
	 *            比較対象オブジェクト1
	 * @param object2
	 *            比較対象オブジェクト2
	 */
	protected boolean equalsResult(Object object1, Object object2){
		return getEqualsLastPredicate().test(object1, object2);
	}

	@Override
	public EqualsHandler clone(){
		EqualsHandler clone;
		try {
			clone = (EqualsHandler)super.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}
}
