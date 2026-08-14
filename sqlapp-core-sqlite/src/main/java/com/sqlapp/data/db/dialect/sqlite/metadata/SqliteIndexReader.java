/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sqlite.
 */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaProperties;

/** Calls SQLite JDBC's index API once per table, as required by the driver. */
public class SqliteIndexReader extends JdbcIndexReader {
	public SqliteIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Index> result = list();
		try {
			for (String tableName : tableNames(context)) {
				final ParametersContext tableContext = context.clone();
				tableContext.put(SchemaProperties.TABLE_NAME.getLabel(), tableName);
				result.addAll(getAllIndex(connection.getMetaData(), tableContext,
						false));
			}
			return result;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private List<String> tableNames(final ParametersContext context) {
		final List<String> result = list();
		final Object value = context.get(SchemaProperties.TABLE_NAME.getLabel());
		if (value instanceof Collection<?> collection) {
			collection.forEach(name -> result.add(String.valueOf(name)));
		} else if (value != null && value.getClass().isArray()) {
			for (int i = 0; i < Array.getLength(value); i++) {
				result.add(String.valueOf(Array.get(value, i)));
			}
		} else if (value != null) {
			result.add(String.valueOf(value));
		}
		return result;
	}
}
