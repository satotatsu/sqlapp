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
 * Fieldが存在しない場合の例外
 * 
 * @author tatsuo satoh
 * 
 */
public class FieldNotFoundException extends SqlappException {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 107328200084629983L;

	/**
	 * フィールド名
	 */
	private final String fieldName;
	/**
	 * オブジェクト
	 */
	private final Object object;

	/**
	 * コンストラクタ
	 * 
	 * @param fieldName
	 *            fieldName
	 * @param object
	 *            オブジェクト
	 * @param t
	 *            例外
	 */
	public FieldNotFoundException(String fieldName, Object object, Throwable t) {
		super(createMessage(fieldName, object), t);
		this.fieldName = fieldName;
		this.object = object;
	}

	/**
	 * コンストラクタ
	 * 
	 * @param fieldName
	 *            fieldName
	 * @param object
	 *            オブジェクト
	 */
	public FieldNotFoundException(String fieldName, Object object) {
		super(createMessage(fieldName, object));
		this.fieldName = fieldName;
		this.object = object;
	}

	private static String createMessage(String fieldName, Object object){
		StringBuilder builder=new StringBuilder();
		builder.append("fieldName=");
		builder.append(fieldName);
		builder.append(", object=");
		builder.append(object);
		return builder.toString();
	}


	/**
	 * @return the fieldName
	 */
	public String getFieldName() {
		return fieldName;
	}


	/**
	 * @return the object
	 */
	public Object getObject() {
		return object;
	}


}
