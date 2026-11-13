/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.metadata;

import static com.sqlapp.util.CommonUtils.list;
import static com.sqlapp.util.CommonUtils.tripleKeyMap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPair;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.FlexList;
import com.sqlapp.util.TripleKeyMap;

/**
 * Reads DuckDB foreign keys (DuckDB 1.1+). DuckDB only allows references
 * within the same catalog and schema and supports neither cascading actions nor
 * deferred checking, so update and delete rules are left unspecified (NO
 * ACTION).
 */
public class DuckDbForeignKeyConstraintReader extends ForeignKeyConstraintReader {

	public DuckDbForeignKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		DuckDbMetadataSupport.requireConstraintMetadata(getDialect());
		final SqlNode node = getSqlNodeCache().getString("foreignKeyConstraints.sql");
		final List<ForeignKeyConstraint> result = list();
		final TripleKeyMap<String, String, String, ForeignKeyConstraint> constraints = tripleKeyMap();
		final TripleKeyMap<String, String, String, FlexList<ColumnPair>> columns = tripleKeyMap();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String catalogName = getString(rs, TABLE_CATALOG);
				final String schemaName = getString(rs, TABLE_SCHEMA);
				final String tableName = getString(rs, TABLE_NAME);
				final String name = getString(rs, CONSTRAINT_NAME);
				ForeignKeyConstraint constraint = constraints.get(catalogName, schemaName, name);
				FlexList<ColumnPair> pairs = columns.get(catalogName, schemaName, name);
				if (constraint == null) {
					constraint = new ForeignKeyConstraint(name);
					constraint.setCatalogName(catalogName);
					constraint.setSchemaName(schemaName);
					constraint.setTableName(tableName);
					pairs = new FlexList<ColumnPair>();
					constraints.put(catalogName, schemaName, name, constraint);
					columns.put(catalogName, schemaName, name, pairs);
					result.add(constraint);
				} else if (!tableName.equals(constraint.getTableName())) {
					throw new SQLException("Duplicate DuckDB foreign key name " + name + " in " + catalogName + "."
							+ schemaName + " for tables " + constraint.getTableName() + " and " + tableName);
				}
				final ColumnPair pair = new ColumnPair();
				pair.columnName = getString(rs, COLUMN_NAME);
				pair.refCatalogName = catalogName;
				pair.refSchemaName = schemaName;
				pair.refTableName = getString(rs, "ref_table_name");
				pair.refColumnName = getString(rs, "ref_column_name");
				pairs.add(pair);
			}
		});
		setForeignKeyConstraintColumns(columns, result);
		return result;
	}
}
