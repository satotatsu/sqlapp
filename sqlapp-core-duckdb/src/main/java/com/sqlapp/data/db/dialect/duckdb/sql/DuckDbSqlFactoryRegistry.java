/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.View;

/**
 * SQL factories for DuckDB.
 */
public class DuckDbSqlFactoryRegistry extends SimpleSqlFactoryRegistry {

	public DuckDbSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE, DuckDbCreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER, DuckDbAlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE, DuckDbMergeFactory.class);
		registerSqlFactory(Index.class, SqlType.CREATE, DuckDbCreateIndexFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, DuckDbCreateSequenceFactory.class);
		registerSqlFactory(Sequence.class, SqlType.SEQUENCE_NEXT_VALUES, DuckDbSequenceNextValuesFactory.class);
		for (final Class<?> type : java.util.List.<Class<?>>of(Table.class, View.class, Column.class, Index.class,
				Sequence.class)) {
			registerSqlFactory(type, SqlType.SET_COMMENT, DuckDbSetCommentFactory.class);
		}
	}
}
