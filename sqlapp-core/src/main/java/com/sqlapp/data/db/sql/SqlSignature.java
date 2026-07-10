/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.sql;

import java.util.Collections;
import java.util.Set;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public enum SqlSignature {
	PRIMARY_KEY {
	},
	UNIQUE_KEY {
	},
	PARENT_UNIQUE_KEY {
	},;

	public Set<Column> getKeyColumns(Table table) {
		return Collections.emptySet();
	}

	public Set<Column> keys(Table table) {
		return Collections.emptySet();
	}

	public Set<Column> getNullColumns(Row row, Set<Column> columns) {
		Set<Column> result = CommonUtils.linkedSet();
		for (Column column : columns) {
			Object obj = row.get(column);
			if (obj == null) {
				result.add(column);
			}
		}
		return result;
	}
}
