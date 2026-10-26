/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.metadata.UniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Reads named keys, collation overrides and conflict policies from catalog DDL.
 */
public class SqliteUniqueConstraintReader extends UniqueConstraintReader {
	public SqliteUniqueConstraintReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<UniqueConstraint> result = new ArrayList<>();
		// sqlite_master is available on legacy SQLite; no table-valued PRAGMAs are
		// needed.
		try (var statement = SqliteMetadataQueries.prepare(connection, context, "m.name", "", "");
				var rs = statement.executeQuery()) {
			while (rs.next()) {
				String table = rs.getString("table_name");
				int sequence = 0;
				for (String fragment : SqliteColumnReader.splitColumnDefinitions(rs.getString("table_sql"))) {
					for (var key : SqliteDdl.keys(fragment)) {
						String name = key.name() != null ? key.name()
								: key.primary() ? null : "sqlite_unique_" + table + "_" + (++sequence);
						var constraint = new UniqueConstraint(name, key.primary());
						constraint.setDialect(getDialect());
						constraint.setCatalogName(getCatalogName(context));
						constraint.setSchemaName(getSchemaName(context));
						constraint.setTableName(table);
						if (key.conflict() != null)
							constraint.getSpecifics().put("sqlite.onConflict", key.conflict());
						for (int i = 0; i < key.columns().size(); i++) {
							var column = key.columns().get(i);
							constraint.getColumns().add(new Column(column.name()),
									column.descending() ? Order.Desc : Order.Asc);
							if (column.collation() != null)
								constraint.getSpecifics().put("sqlite.key.collation." + i, column.collation());
						}
						result.add(constraint);
					}
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return result;
	}
}
