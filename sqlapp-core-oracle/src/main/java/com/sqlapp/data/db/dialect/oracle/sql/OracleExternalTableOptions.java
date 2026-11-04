package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.schemas.Table;

/** Shared identification for external-table CREATE and ALTER handling. */
final class OracleExternalTableOptions {
	private OracleExternalTableOptions() {
	}

	static boolean isExternal(Table table) {
		return table != null && ("true".equalsIgnoreCase(table.getSpecifics().get("oracle.table.external"))
				|| table.getParent() != null && table.getParent().getSchema() != null
						&& table.getParent().getSchema().getExternalTables().get(table.getName()) != null);
	}
}
