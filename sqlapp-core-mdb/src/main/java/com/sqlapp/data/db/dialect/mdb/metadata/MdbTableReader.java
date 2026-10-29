/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcTableReader;
import com.sqlapp.data.db.dialect.mdb.util.MdbRelationshipSupport;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.db.metadata.ForeignKeyConstraintReader;
import com.sqlapp.data.db.metadata.IndexReader;
import com.sqlapp.data.db.metadata.TableObjectReader;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;

/** Reads Access table details in bulk, with a generic JDBC fallback. */
public class MdbTableReader extends JdbcTableReader {

	public MdbTableReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new MdbColumnReader(this.getDialect());
	}

	@Override
	protected IndexReader newIndexReader() {
		return new MdbIndexReader(this.getDialect());
	}

	@Override
	protected UniqueConstraintReader newUniqueConstraintReader() {
		return new MdbPrimaryKeyConstraintReader(this.getDialect());
	}

	@Override
	protected ForeignKeyConstraintReader newForeignKeyConstraintReader() {
		return new MdbForeignKeyConstraintReader(this.getDialect());
	}

	@Override
	protected void setMetadataDetail(final Connection connection, final ParametersContext context,
			final List<Table> tables) throws SQLException {
		if (tables.isEmpty()) {
			return;
		}
		try {
			if (MdbMetadataSupport.database(connection) != null) {
				loadAccessDetails(connection, tables);
				return;
			}
		} catch (final IOException e) {
			throw new RuntimeException("Failed to read Access table details", e);
		}
		for (final Table table : tables) {
			table.setDialect(this.getDialect());
			loadTableObjects(connection, getColumnReader(), table);
			loadTableObjects(connection, getUniqueConstraintReader(), table);
			loadTableObjects(connection, getIndexReader(), table);
			loadTableObjects(connection, getForeignKeyConstraintReader(), table);
		}
	}

	private void loadAccessDetails(final Connection connection, final List<Table> tables) {
		final Map<String, Table> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (final Table table : tables) {
			table.setDialect(getDialect());
			byName.put(table.getName(), table);
		}
		// Each reader runs once. Attach all columns before binding constraints.
		for (final var column : getColumnReader().getAllFull(connection)) {
			final Table table = byName.get(column.getTableName());
			if (table != null) {
				table.getColumns().add(column);
			}
		}
		for (final var key : getUniqueConstraintReader().getAllFull(connection)) {
			final Table table = byName.get(key.getTableName());
			if (table != null) {
				table.getConstraints().add(key);
			}
		}
		for (final var index : getIndexReader().getAllFull(connection)) {
			final Table table = byName.get(index.getTableName());
			if (table != null) {
				table.getIndexes().add(index);
			}
		}
		for (final var key : getForeignKeyConstraintReader().getAllFull(connection)) {
			final Table table = byName.get(key.getTableName());
			if (table != null) {
				MdbRelationshipSupport.bindColumns(key, table, byName.get(key.getRelatedTableName()));
				table.getConstraints().add(key);
			}
		}
	}

	private void loadTableObjects(final Connection connection, final TableObjectReader<?> reader, final Table table) {
		reader.setCatalogName(table.getCatalogName());
		reader.setSchemaName(table.getSchemaName());
		reader.setObjectName(table.getName());
		setTableObjects(connection, reader, table);
	}
}
