package com.sqlapp.data.db.dialect.sqlserver.util;

import java.sql.SQLException;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;

public enum SqlServerTableOptions {
	LOCK_ESCALATION() {
		@Override
		public void setTable(final ExResultSet rs, Table table) throws SQLException {
			if (!rs.contains(this.toString())) {
				table.getSpecifics().remove(this.toString());
				return;
			}
			setTable(table, rs.getString(this.toString()));
		}
		@Override
		public void setTable(Table table, String value) {
			if ("AUTO".equals(value)||"TABLE".equals(value)||"DISABLE".equals(value)) {
				table.getSpecifics().put(this.toString(), value.toString());
			} else {
				table.getSpecifics().put(this.toString(), "TABLE");
			}
		}
	};

	public Object getDefaultValue() {
		return null;
	}

	public void setTable(Table table, String value) {
	}

	public void setTable(final ExResultSet rs, Table table) throws SQLException {	
	}
}
