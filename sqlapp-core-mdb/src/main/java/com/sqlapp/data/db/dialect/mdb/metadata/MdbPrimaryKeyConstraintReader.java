/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.metadata;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcPrimaryKeyConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Reads original Access primary-key names and column order from the file. */
public class MdbPrimaryKeyConstraintReader extends JdbcPrimaryKeyConstraintReader {

	public MdbPrimaryKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		try {
			final var database = MdbMetadataSupport.database(connection);
			if (database == null) {
				final List<UniqueConstraint> constraints = super.doGetAll(connection, context, productVersionInfo);
				constraints.forEach(constraint -> constraint.setPrimaryKey(true));
				return constraints;
			}
			final List<UniqueConstraint> constraints = new ArrayList<>();
			for (final String name : MdbMetadataSupport.tableNames(database, getTableName(context))) {
				final var table = database.getTable(name);
				if (table == null) {
					continue;
				}
				for (final var source : table.getIndexes()) {
					if (!source.isPrimaryKey()
							|| !MdbMetadataSupport.matchesName(getObjectName(context), source.getName())) {
						continue;
					}
					final UniqueConstraint constraint = new UniqueConstraint(source.getName());
					constraint.setPrimaryKey(true);
					constraint.setTableName(table.getName());
					for (final var column : source.getColumns()) {
						constraint.getColumns().add(column.getName());
					}
					constraints.add(constraint);
				}
			}
			return constraints;
		} catch (final SQLException | IOException e) {
			throw new RuntimeException("Failed to read Access primary keys", e);
		}
	}
}
