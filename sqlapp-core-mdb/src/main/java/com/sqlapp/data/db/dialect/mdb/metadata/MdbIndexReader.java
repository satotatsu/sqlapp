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
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.db.dialect.mdb.MdbFileLoader;
import com.sqlapp.data.db.dialect.mdb.util.MdbRelationshipSupport;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Table;

/**
 * Reads physical Access indexes instead of indexes in UCanAccess's SQL mirror.
 */
public class MdbIndexReader extends JdbcIndexReader {

	public MdbIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	public void loadFull(final Connection connection, final Table table) {
		// Physical primary indexes are already excluded by doGetAll.
		table.getIndexes().addAll(getAllFull(connection));
	}

	@Override
	public void load(final Connection connection, final Table table) {
		table.getIndexes().addAll(getAll(connection));
	}

	@Override
	protected List<Index> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		try {
			final var database = MdbMetadataSupport.database(connection);
			if (database == null) {
				return super.doGetAll(connection, context, productVersionInfo);
			}
			final List<Index> indexes = new ArrayList<>();
			for (final String name : MdbMetadataSupport.tableNames(database, getTableName(context))) {
				final var table = database.getTable(name);
				if (table == null) {
					continue;
				}
				for (final var source : table.getIndexes()) {
					if (source.isPrimaryKey() || MdbRelationshipSupport.isRelationshipIndex(source)
							|| !MdbMetadataSupport.matchesName(getObjectName(context), source.getName())) {
						continue;
					}
					final Index index = new Index(source.getName());
					index.setTableName(table.getName());
					index.setUnique(source.isUnique());
					if (source.shouldIgnoreNulls()) {
						index.getSpecifics().put(MdbFileLoader.INDEX_IGNORE_NULLS, Boolean.TRUE.toString());
					}
					for (final var column : source.getColumns()) {
						index.getColumns().add(new Column(column.getName()),
								column.isAscending() ? Order.Asc : Order.Desc);
					}
					indexes.add(index);
				}
			}
			return indexes;
		} catch (final SQLException | IOException e) {
			throw new RuntimeException("Failed to read Access indexes", e);
		}
	}
}
