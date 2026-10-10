/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresTableReader;
import com.sqlapp.data.db.metadata.ColumnReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

public class CockroachTableReader extends PostgresTableReader {
	public CockroachTableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected com.sqlapp.data.db.metadata.IndexReader newIndexReader() {
		return new CockroachIndexReader(getDialect());
	}

	@Override
	protected ColumnReader newColumnReader() {
		return new CockroachColumnReader(getDialect());
	}

	@Override
	protected List<Table> doGetAll(Connection c, ParametersContext context, ProductVersionInfo version) {
		var result = new ArrayList<Table>();
		try (var sql = c.createStatement();
				var rows = sql.executeQuery(
						"SELECT n.nspname,c.relname,c.oid,obj_description(c.oid,'pg_class') FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind='r' ORDER BY n.nspname,c.relname")) {
			while (rows.next()) {
				String schema = rows.getString(1), name = rows.getString(2);
				if (!CockroachMetadata.userSchema(schema) || !CockroachMetadata.matches(context, "schemaName", schema)
						|| !CockroachMetadata.matches(context, "tableName", name))
					continue;
				result.add(
						createTable(name).setSchemaName(schema).setId(rows.getString(3)).setRemarks(rows.getString(4)));
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Cannot read CockroachDB tables", e);
		}
		return result;
	}

	@Override
	protected void setMetadataDetail(Connection c, ParametersContext context, List<Table> tables) throws SQLException {
		super.setMetadataDetail(c, context, tables);
		for (var table : tables) {
			table.setDefinition(CockroachMetadata.definition(c, "TABLE", table.getSchemaName(), table.getName()));
			String locality = com.sqlapp.data.db.dialect.cockroach.util.CockroachPlacement
					.readLocality(String.join("\n", table.getDefinition()));
			if (locality != null)
				table.getSpecifics().put("COCKROACH_LOCALITY", locality);
		}
	}
}
