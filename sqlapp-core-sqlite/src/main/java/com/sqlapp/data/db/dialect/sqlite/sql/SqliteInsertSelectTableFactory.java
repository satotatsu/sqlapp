/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/** Appends modeled source rows without suppressing constraint violations. */
public class SqliteInsertSelectTableFactory extends SqliteInsertFactory {
	@Override
	protected SqlType getSqlType() {
		return SqlType.INSERT_SELECT_TABLE;
	}

	@Override
	public List<SqlOperation> createSql(Table target) {
		return createSql(target, SqliteTableSource.resolve(target, getTableOptions()));
	}

	/** Uses an explicit source model, including a table in an attached database. */
	public List<SqlOperation> createSql(Table target, Table source) {
		var columns = SqliteTableSource.columns(target, source, getTableOptions(), this::isInsertable);
		var builder = createSqlBuilder();
		SqliteTableSource.addInsertSelect(target, source, columns, getTableOptions(), builder);
		var operations = new ArrayList<SqlOperation>();
		addSql(operations, builder, getSqlType(), target);
		return operations;
	}
}
