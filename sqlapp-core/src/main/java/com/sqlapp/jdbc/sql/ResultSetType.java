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
package com.sqlapp.jdbc.sql;

import com.sqlapp.data.converter.EnumConvertable;

/**
 * ResultSetタイプのenum
 * 
 * @author tatsuo satoh
 *
 */
public enum ResultSetType implements EnumConvertable<Integer> {
	/**
	 * @see java.sql.ResultSet#TYPE_FORWARD_ONLY
	 */
	TYPE_FORWARD_ONLY(java.sql.ResultSet.TYPE_FORWARD_ONLY),
	/**
	 * @see java.sql.ResultSet#TYPE_SCROLL_SENSITIVE
	 */
	TYPE_SCROLL_SENSITIVE(java.sql.ResultSet.TYPE_SCROLL_SENSITIVE),
	/**
	 * @see java.sql.ResultSet#TYPE_SCROLL_INSENSITIVE
	 * 
	 */
	TYPE_SCROLL_INSENSITIVE(java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE);

	private final Integer value;

	private final String text;

	ResultSetType(int value) {
		this.value = value;
		this.text = name().replace("_", "");
	}

	/**
	 * @return the value
	 */
	public Integer getValue() {
		return value;
	}

	/**
	 * 数値をenumに変換します。
	 * 
	 * @param value
	 * @return enumの値
	 */
	public static ResultSetType parse(Integer value) {
		for (ResultSetType type : values()) {
			if (type.getValue().equals(value)) {
				return type;
			}
		}
		return getDefault();
	}

	/**
	 * 文字列をenumに変換します。
	 * 
	 * @param value
	 * @return enumの値
	 */
	public static ResultSetType parse(String value) {
		if (value == null) {
			return getDefault();
		}
		value = value.replace("_", "");
		for (ResultSetType type : values()) {
			if (type.text.equalsIgnoreCase(value)) {
				return type;
			}
			if (type.getValue().toString().equals(value)) {
				return type;
			}
		}
		return getDefault();
	}

	/**
	 * デフォルト値を取得します。
	 * 
	 * @return デフォルト値
	 */
	public static ResultSetType getDefault() {
		return TYPE_FORWARD_ONLY;
	}

}