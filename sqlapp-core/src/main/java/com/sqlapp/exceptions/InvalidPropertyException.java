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
package com.sqlapp.exceptions;

/**
 * プロパティが不正な場合の例外
 * 
 * @author tatsuo satoh
 * 
 */
public class InvalidPropertyException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	/**
	 * プロパティ名
	 */
	private final String propertyName;
	/**
	 * プロパティの値
	 */
	private final Object propertyValue;
	/**
	 * プロパティの型
	 */
	private final Class<?> propertyClass;

	/**
	 * コンストラクタ
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param propertyValue
	 *            プロパティ値
	 * @param propertyClass
	 *            プロパティクラス
	 * @param t
	 *            例外
	 */
	public InvalidPropertyException(String propertyName, Object propertyValue,
			Class<?> propertyClass, Throwable t) {
		super(createMessage(propertyName, propertyValue, propertyClass), t);
		this.propertyName = propertyName;
		this.propertyValue = propertyValue;
		this.propertyClass = propertyClass;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param propertyValue
	 *            プロパティ値
	 * @param propertyClass
	 *            プロパティクラス
	 */
	public InvalidPropertyException(String propertyName, Object propertyValue,
			Class<?> propertyClass) {
		super(createMessage(propertyName, propertyValue, propertyClass));
		this.propertyName = propertyName;
		this.propertyValue = propertyValue;
		this.propertyClass = propertyClass;
	}
	
	private static String createMessage(String propertyName, Object propertyValue,
			Class<?> propertyClass){
		StringBuilder builder=new StringBuilder();
		builder.append("propertyName=");
		builder.append(propertyName);
		builder.append(", propertyValue=");
		builder.append(propertyValue);
		if (propertyClass!=null){
			builder.append(", propertyClass=");
			builder.append(propertyClass);
		};
		return builder.toString();
	}

	/**
	 * コンストラクタ
	 * 
	 * @param propertyName
	 *            プロパティ名
	 * @param propertyValue
	 *            プロパティ値
	 */
	public InvalidPropertyException(String propertyName, Object propertyValue) {
		this(propertyName, propertyValue, null);
	}

	/**
	 * @return the propertyName
	 */
	public String getPropertyName() {
		return propertyName;
	}

	/**
	 * @return the propertyClass
	 */
	public Class<?> getPropertyClass() {
		return propertyClass;
	}

	/**
	 * @return the propertyValue
	 */
	public Object getPropertyValue() {
		return propertyValue;
	}

}
