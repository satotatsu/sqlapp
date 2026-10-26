/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/** SQLite INSERT SELECT UPSERT using modeled staging and target tables. */
public class SqliteMergeTableFactory extends SqliteMergeFactory {
	@Override
	protected SqlType getSqlType() {
		return SqlType.MERGE_TABLE;
	}

	@Override
	public List<SqlOperation> createSql(Table target) {
		return createSql(target, SqliteTableSource.resolve(target, getTableOptions()));
	}

	/** Explicit source identity supports permanent or attached staging tables. */
	public List<SqlOperation> createSql(Table target, Table source) {
		validateMergeOptions(target);
		var columns = SqliteTableSource.columns(target, source, getTableOptions(), this::isInsertable);
		var builder = createSqlBuilder();
		SqliteTableSource.addInsertSelect(target, source, columns, getTableOptions(), builder);
		// Disambiguates ON CONFLICT from a SELECT join clause on SQLite 3.24+.
		builder.lineBreak()._add("WHERE 1=1");
		addConflictClause(target, createSqlSignature(target), columns, builder);
		addReturning(target, builder);
		var operations = new ArrayList<SqlOperation>();
		addSql(operations, builder, getSqlType(), target);
		return operations;
	}
}
