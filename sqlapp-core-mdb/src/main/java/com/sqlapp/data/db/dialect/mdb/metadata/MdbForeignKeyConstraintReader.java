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
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcForeignKeyConstraintReader;
import com.sqlapp.data.db.dialect.mdb.util.MdbRelationshipSupport;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Reads Access relationships in one file-metadata operation. */
public class MdbForeignKeyConstraintReader extends JdbcForeignKeyConstraintReader {

	public MdbForeignKeyConstraintReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ForeignKeyConstraint> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		try {
			final var database = MdbMetadataSupport.database(connection);
			if (database == null) {
				return super.doGetAll(connection, context, productVersionInfo);
			}
			final List<ForeignKeyConstraint> keys = new ArrayList<>();
			for (final var source : database.getRelationships()) {
				if (MdbMetadataSupport.matchesName(getTableName(context), source.getToTable().getName())
						&& MdbMetadataSupport.matchesName(getObjectName(context), source.getName())) {
					keys.add(MdbRelationshipSupport.toForeignKey(source));
				}
			}
			return keys;
		} catch (final SQLException | IOException e) {
			throw new RuntimeException("Failed to read Access relationships", e);
		}
	}
}
