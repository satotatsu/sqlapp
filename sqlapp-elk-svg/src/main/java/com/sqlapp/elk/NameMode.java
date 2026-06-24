/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-virtica.
 *
 * sqlapp-core-virtica is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-virtica is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-virtica.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public enum NameMode {
	NORMAL() {
	},
	LOGICAL() {
		@Override
		public String getName(Schema schema) {
			return CommonUtils.coalesce(schema.getDisplayName(), schema.getName());
		}

		@Override
		public String getName(Column column) {
			return CommonUtils.coalesce(column.getDisplayName(), column.getName());
		}

		@Override
		public String getName(Table table) {
			return CommonUtils.coalesce(table.getDisplayName(), table.getName());
		}
	};

	public String getName(Schema schema) {
		return schema.getName();
	}

	public String getName(Column column) {
		return column.getName();
	}

	public String getName(Table table) {
		return table.getName();
	}
}
