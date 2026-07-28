/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.data.db.dialect.oracle.sql.OracleAnnotationUtils;

public class Oracle23aiTableReader extends Oracle12cTableReader {

	protected Oracle23aiTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new Oracle23aiColumnReader(getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new Oracle23aiIndexReader(getDialect());
	}

	@Override
	protected void setMetadataDetail(final Connection connection,
			final ParametersContext context, final List<Table> tables)
			throws SQLException {
		super.setMetadataDetail(connection, context, tables);
		if (tables == null || tables.isEmpty()) {
			return;
		}
		final SqlNode node = getSqlNodeCache().getString("annotations.sql");
		try {
			execute(connection, node, context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(final ExResultSet rs)
						throws SQLException {
					applyAnnotation(rs, tables);
				}
			});
		} catch (final RuntimeException e) {
			if (!Oracle23aiIndexReader.isVectorIndexDetailUnavailable(e)) {
				throw e;
			}
			logger.warn("Oracle schema annotation metadata is unavailable or "
					+ "not permitted; annotations are skipped. "
					+ e.getMessage());
		}
	}

	private void applyAnnotation(final ExResultSet rs,
			final List<Table> tables) throws SQLException {
		final String owner = getString(rs, "ANNOTATION_OWNER");
		final String objectName = getString(rs, "OBJECT_NAME");
		final String objectType = getString(rs, "OBJECT_TYPE");
		final String columnName = getString(rs, "COLUMN_NAME");
		AbstractDbObject<?> target = null;
		if ("TABLE".equalsIgnoreCase(objectType)) {
			final Table table = findTable(tables, owner, objectName);
			if (table != null) {
				target = columnName == null ? table
						: table.getColumns().get(columnName);
			}
		} else if ("INDEX".equalsIgnoreCase(objectType)) {
			for (Table table : tables) {
				final Index index = table.getIndexes().get(objectName);
				if (index != null && (owner == null
						|| owner.equalsIgnoreCase(index.getSchemaName())
						|| owner.equalsIgnoreCase(table.getSchemaName()))) {
					target = index;
					break;
				}
			}
		}
		if (target != null) {
			OracleAnnotationUtils.setAnnotation(target,
					getString(rs, "ANNOTATION_NAME"),
					getString(rs, "ANNOTATION_VALUE"));
		}
	}

	private Table findTable(final List<Table> tables, final String owner,
			final String name) {
		for (Table table : tables) {
			if (name.equalsIgnoreCase(table.getName())
					&& (owner == null
							|| owner.equalsIgnoreCase(table.getSchemaName()))) {
				return table;
			}
		}
		return null;
	}
}
