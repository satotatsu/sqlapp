/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-informix.
 */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;

/** Adds Informix index ordering that the JDBC driver omits. */
public class InformixIndexReader extends JdbcIndexReader {
	public InformixIndexReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final List<Index> indexes = super.doGetAll(connection, context, productVersionInfo);
		try {
			for (Index index : indexes) {
				loadOrdering(connection, index);
			}
			return indexes;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private void loadOrdering(final Connection connection, final Index index)
			throws SQLException {
		final StringBuilder sql = new StringBuilder("SELECT ");
		for (int i = 1; i <= 16; i++) {
			if (i > 1) {
				sql.append(',');
			}
			sql.append("part").append(i);
		}
		sql.append(" FROM sysindexes WHERE idxname=? AND tabid IN "
				+ "(SELECT tabid FROM systables WHERE tabname=?)");
		try (var statement = connection.prepareStatement(sql.toString())) {
			statement.setString(1, index.getName());
			statement.setString(2, index.getTableName());
			try (var resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return;
				}
				for (int i = 0; i < index.getColumns().size() && i < 16; i++) {
					final int part = resultSet.getInt(i + 1);
					index.getColumns().get(i).setOrder(part < 0 ? Order.Desc : Order.Asc);
				}
			}
		}
	}
}
