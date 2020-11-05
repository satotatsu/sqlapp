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

import static com.sqlapp.util.CommonUtils.eq;
import static com.sqlapp.util.CommonUtils.eqIgnoreCase;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.converter.Converters;

/**
 * DIFFを出力するするためのビルダー
 * 
 * @author satoh
 * 
 */
public class DiffStringBuilder implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -2873994302982754793L;
	/**
	 * クラス名
	 */
	private String className = null;
	private SeparatedStringBuilder builder = new SeparatedStringBuilder(", ");

	private boolean exceptEmpty = true;
	/**
	 * オブジェクトを文字列化するためのコンバーター
	 */
	private Converters converters = Converters.getNewBooleanTrueInstance();

	/**
	 * コンストラクタ
	 * 
	 * @param className
	 */
	public DiffStringBuilder(String className) {
		this.className = className;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param clazz
	 */
	public DiffStringBuilder(Class<?> clazz) {
		this.className = clazz.getSimpleName();
	}

	/**
	 * 差分要素を比較します
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param value1
	 * @param value2
	 */
	public DiffStringBuilder setDiff(String propertyName, Object value1,
			Object value2) {
		if (exceptEmpty && CommonUtils.isEmpty(value1)) {
			return this;
		}
		if (eq(value1, value2)) {
			return this;
		}
		if ((value1 instanceof Collection) && (value2 instanceof Collection)) {
			return this.setDiff(propertyName, (Collection<?>) value1,
					(Collection<?>) value2);
		} else {
			addElement(propertyName, value1);
		}
		return this;
	}

	/**
	 * 差分要素を比較します
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param map1
	 * @param map2
	 */
	public DiffStringBuilder setDiff(String propertyName, Map<?,?> map1,
			Map<?,?> map2) {
		if (exceptEmpty && CommonUtils.isEmpty(map1)) {
			return this;
		}
		if (eq(map1, map2)) {
			return this;
		}
		if (CommonUtils.isEmpty(map2)) {
			addElement(propertyName, map1);
			return this;
		}
		Set<Object> keys=CommonUtils.treeSet();
		keys.addAll(map1.keySet());
		keys.addAll(map2.keySet());
		DiffStringBuilder builder=new DiffStringBuilder(propertyName);
		for(Object prop:keys){
			Object value1=map1.get(prop);
			Object value2=map2.get(prop);
			builder.setDiff(prop.toString(), value1, value2);
		}
		this.add(builder);
		return this;
	}

	
	/**
	 * 大文字小文字を無視して差分要素を比較します
	 * 
	 * @param propertyName
	 * @param value1
	 * @param value2
	 */
	public DiffStringBuilder setDiffIgnoreCase(String propertyName,
			String value1, String value2) {
		if (exceptEmpty && CommonUtils.isEmpty(value1)) {
			return this;
		}
		if (eqIgnoreCase(value1, value2)) {
			return this;
		}
		addElement(propertyName, value1);
		return this;
	}

	/**
	 * コレクションの差分要素を追加します
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param value1
	 *            コレクション1
	 * @param value2
	 *            コレクション2
	 */
	public DiffStringBuilder setDiff(String propertyName, Collection<?> value1,
			Collection<?> value2) {
		if (exceptEmpty) {
			if (CommonUtils.isEmpty(value1)) {
				return this;
			}
		}
		if (eq(value1, value2)) {
			return this;
		}
		SeparatedStringBuilder sep = new SeparatedStringBuilder("\n");
		sep.setStart("(").setEnd(")");
		sep.add(value1);
		addElement("\n" + propertyName, sep.toString());
		return this;
	}

	/**
	 * 差分要素を追加します。
	 * 
	 * @param value
	 */
	public DiffStringBuilder setDiff(Object value) {
		builder.add(value);
		return this;
	}
	
	/**
	 * 差分要素を追加します。
	 * 
	 * @param value
	 */
	public DiffStringBuilder setDiff(DiffStringBuilder value) {
		if (!value.isEmpty()){
			builder.add(value);
		}
		return this;
	}

	public DiffStringBuilder addElement(String propertyName, Object value) {
		if (value == null) {
			return this;
		} else if (converters.isConvertable(value.getClass())) {
			addElement(propertyName, converters.convertString(value));
		} else {
			addElement(propertyName, value.toString());
		}
		return this;
	}

	private void addElement(String propertyName, String value) {
		builder.add(propertyName + "=" + value);
	}

	/**
	 * 要素の追加を行います
	 * 
	 * @param diffStringBuilder
	 *            追加対象の要素
	 */
	public DiffStringBuilder add(DiffStringBuilder diffStringBuilder) {
		this.builder.add(diffStringBuilder.builder.getElements());
		return this;
	}

	@Override
	public String toString() {
		String val = builder.toString();
		StringBuilder result = new StringBuilder(this.className.length()
				+ val.length() + 2);
		result.append(this.className).append('[').append(val).append(']');
		return result.toString();
	}
	
	public boolean isEmpty(){
		return this.builder.size()==0;
	}
}
