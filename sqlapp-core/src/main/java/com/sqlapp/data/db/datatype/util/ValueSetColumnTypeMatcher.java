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
						String[] vals = value.split(",");
						List<String> set = CommonUtils.list();
						for (String val : vals) {
							set.add(trim(val));
						}
						c.setValues(set);
					}
				});
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

}
