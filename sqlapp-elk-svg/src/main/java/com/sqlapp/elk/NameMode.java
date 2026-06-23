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
