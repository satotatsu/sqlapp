/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.Table;

/**
 * SQLite requires a caller-owned transaction rather than LOCK TABLE/FOR UPDATE
 * SQL.
 */
public class SqliteLockTableFactory extends AbstractTableFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		throw new IllegalArgumentException("SQLite does not support LOCK TABLE or SELECT FOR UPDATE; "
				+ "begin a transaction with the required mode on the connection before accessing " + table.getName());
	}
}
