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

package com.sqlapp.data.db.datatype.util;

import static com.sqlapp.util.CommonUtils.trim;

import java.util.List;
import java.util.Optional;

import com.sqlapp.util.CommonUtils;

/**
 * 正規表現でValue Setを持つカラム(ENUM or SET)の型を処理します
 */
public class ValueSetColumnTypeMatcher implements ColumnTypeMatcher {

	private final String dataTypeName;

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public ValueSetColumnTypeMatcher(String dataTypeName) {
		this.dataTypeName = dataTypeName;
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				"(?<dataTypeName>" + dataTypeName + ")\\s*\\((?<values>.*)\\)", (m, c) -> {
					String value = m.group("dataTypeName");
					if (value != null) {
						c.setDataTypeName(value);
					}
					value = m.group("values");
					if (value != null) {
						c.setValues(splitValues(value));
					}
				});
	}

	/** Splits outside quoted literals, retaining their original quoting and escapes. */
	private static List<String> splitValues(String value) {
		List<String> values = CommonUtils.list();
		int start = 0;
		char quote = 0;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (quote != 0) {
				if (ch == '\\') {
					i++;
				} else if (ch == quote) {
					if (i + 1 < value.length() && value.charAt(i + 1) == quote) {
						i++;
					} else {
						quote = 0;
					}
				}
			} else if (ch == '\'' || ch == '"') {
				quote = ch;
			} else if (ch == ',') {
				values.add(trim(value.substring(start, i)));
				start = i + 1;
			}
		}
		if (quote != 0) {
			throw new IllegalArgumentException("Unterminated quoted value: " + value);
		}
		values.add(trim(value.substring(start)));
		return values;
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

}
