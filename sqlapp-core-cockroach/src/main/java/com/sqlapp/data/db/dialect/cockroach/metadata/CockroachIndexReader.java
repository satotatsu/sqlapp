/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

/**
 * Retains vendor index syntax as well as ordinary modeled
 * keys/includes/predicates.
 */
public class CockroachIndexReader extends PostgresIndexReader {
	public CockroachIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Index> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = super.doGetAll(c, context, version);
		String query = "SELECT pg_get_indexdef(i.indexrelid) FROM pg_catalog.pg_index i JOIN pg_catalog.pg_class t ON t.oid=i.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace JOIN pg_catalog.pg_class x ON x.oid=i.indexrelid WHERE n.nspname=? AND t.relname=? AND x.relname=?";
		try (var sql = c.prepareStatement(query)) {
			for (var index : result) {
				sql.setString(1, index.getSchemaName());
				sql.setString(2, index.getTableName());
				sql.setString(3, index.getName());
				try (var rows = sql.executeQuery()) {
					if (!rows.next())
						throw new SQLException("CockroachDB index definition missing: " + index.getName());
					index.setDefinition(rows.getString(1));
					if (rows.next())
						throw new SQLException("Ambiguous CockroachDB index: " + index.getName());
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB index definition", e);
		}
		return result;
	}
}
