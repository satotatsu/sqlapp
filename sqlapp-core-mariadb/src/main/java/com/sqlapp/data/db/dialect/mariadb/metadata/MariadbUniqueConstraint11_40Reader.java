/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlUniqueConstraintReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Enriches period keys before the shared object predicate filters constraints.
 */
class MariadbUniqueConstraint11_40Reader extends MySqlUniqueConstraintReader {
	MariadbUniqueConstraint11_40Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<UniqueConstraint> doGetAll(Connection connection, ParametersContext context,
			ProductVersionInfo version) {
		List<UniqueConstraint> constraints = super.doGetAll(connection, context, version);
		readKeyPeriods(connection, context, constraints);
		return constraints;
	}

	protected void readKeyPeriods(Connection connection, ParametersContext context,
			List<UniqueConstraint> constraints) {
		if (constraints.isEmpty())
			return;
		Map<List<String>, UniqueConstraint> byIdentity = new HashMap<>();
		Set<List<String>> tables = new HashSet<>();
		for (UniqueConstraint constraint : constraints) {
			var table = Arrays.asList(constraint.getCatalogName(), constraint.getSchemaName(),
					constraint.getTableName());
			tables.add(table);
			byIdentity.put(Arrays.asList(constraint.getCatalogName(), constraint.getSchemaName(),
					constraint.getTableName(), constraint.getName()), constraint);
		}
		execute(connection, getSqlNodeCache().getString("keyPeriods.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				String catalog = getString(rs, "TABLE_CATALOG");
				String schema = getString(rs, "TABLE_SCHEMA");
				String table = getString(rs, "TABLE_NAME");
				if (!tables.contains(Arrays.asList(catalog, schema, table)))
					return;
				String name = getString(rs, "CONSTRAINT_NAME");
				String period = getString(rs, "PERIOD_NAME");
				UniqueConstraint constraint = byIdentity.get(Arrays.asList(catalog, schema, table, name));
				if (constraint == null || period == null || period.isBlank()) {
					throw new IllegalStateException("Cannot resolve MariaDB period key: " + table + "." + name);
				}
				constraint.getSpecifics().put("mariadb.withoutOverlapsPeriod", period);
			}
		});
	}
}
