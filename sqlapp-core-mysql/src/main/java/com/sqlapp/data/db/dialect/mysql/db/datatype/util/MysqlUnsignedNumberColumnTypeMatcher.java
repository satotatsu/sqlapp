/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.mysql.db.datatype.util;

import java.util.Optional;

import com.sqlapp.data.db.datatype.util.ColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.RegexColumnTypeMatcher;
import com.sqlapp.data.db.datatype.util.TypeInformation;

/**
 * MySQLでのUnsigned数値型カラムの型を処理します
 */
public class MysqlUnsignedNumberColumnTypeMatcher implements ColumnTypeMatcher {

	private final String dataTypeName;

	private ColumnTypeMatcher internalColumnTypeMatcher;

	public MysqlUnsignedNumberColumnTypeMatcher(String... dataTypeName) {
		this.dataTypeName = dataTypeName[0];
		this.internalColumnTypeMatcher = new RegexColumnTypeMatcher(
				MySqlColumnTypeMatcherUtils.joinForNumber(dataTypeName)
						+ "\\s*(\\(((?<length>\\s*[0-9]+)\\s*)\\))?(?<unsigned>\\s*unsigned)(?<zerofill>\\s*zerofill)?",
				new MySqlNumberMatcherColumn());
	}

	public String getDataTypeName() {
		return dataTypeName;
	}

	@Override
	public Optional<TypeInformation> match(String productDataType) {
		return internalColumnTypeMatcher.match(productDataType);
	}

}
