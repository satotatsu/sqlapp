/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.jdbc.sql;

import java.sql.Statement;

/**
 * Statement実行時の結果
 */
public enum GeneratedKey {
	/**
	 * The constant indicating that generated keys should be made available for
	 * retrieval.
	 */
	RETURN_GENERATED_KEYS(Statement.RETURN_GENERATED_KEYS) {
	},
	/**
	 * The constant indicating that generated keys should not be made available for
	 * retrieval.
	 */
	NO_GENERATED_KEYS(Statement.NO_GENERATED_KEYS) {
	};

	private final int value;

	private final String text;

	GeneratedKey(int value) {
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
	public static GeneratedKey parse(Integer value) {
		for (GeneratedKey type : values()) {
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
	public static GeneratedKey parse(String value) {
		if (value == null) {
			return getDefault();
		}
		value = value.replace("_", "");
		for (GeneratedKey type : values()) {
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
	public static GeneratedKey getDefault() {
		return null;
	}
}
