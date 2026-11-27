/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2RowPermissions;
import com.sqlapp.data.db.metadata.MaskReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Mask;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Row and column access control (SYSCONTROLS). Masks use the shared Mask model;
 * explicit row permissions and table activation states use the Db2 schema
 * snapshot shared with the LUW dialect. Implicit default permissions are
 * recreated by activation and are not read.
 */
final class Db2HostSecurityReaders {
	/** Transient table marker from tables.sql: blank, R, C or B. */
	static final String ACCESS_CONTROL = "DB2_HOST_ACCESS_CONTROL";

	private Db2HostSecurityReaders() {
	}

	static class Masks extends MaskReader {
		private final Class<?> resources;

		Masks(Dialect dialect, Class<?> resources) {
			super(dialect);
			this.resources = resources;
		}

		@Override
		protected List<Mask> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
			List<Mask> result = new ArrayList<>();
			execute(connection, getSqlNodeCache(resources).getString("masks.sql"), context, new ResultSetNextHandler() {
				@Override
				public void handleResultSetNext(ExResultSet rs) throws SQLException {
					var mask = new Mask(id(rs, "CONTROL_NAME"));
					mask.setSchemaName(id(rs, "SCHEMA_NAME"));
					mask.setTableSchemaName(id(rs, "TABLE_SCHEMA"));
					mask.setTableName(id(rs, "TABLE_NAME"));
					mask.setColumnName(id(rs, "COLUMN_NAME"));
					mask.setEnable(flag(rs, "ENABLE_FLAG"));
					mask.getSpecifics().put("SOURCE_VALID", flag(rs, "VALID_FLAG") ? "Y" : "N");
					String correlation = id(rs, "CORRELATION_NAME");
					if (correlation != null && !correlation.isBlank())
						mask.getSpecifics().put("TABCORRELATION", correlation);
					mask.setStatement(rule(rs));
					mask.setRemarks(rs.getString("REMARKS"));
					result.add(mask);
				}
			});
			return result;
		}
	}

	/** Maps one explicit row permission from permissions.sql. */
	static Db2RowPermissions.Permission permission(ExResultSet rs) throws SQLException {
		if (!"A".equals(id(rs, "ENFORCED")))
			throw new SQLException("Unsupported row permission enforcement: " + id(rs, "CONTROL_NAME"));
		String correlation = id(rs, "CORRELATION_NAME");
		return new Db2RowPermissions.Permission(id(rs, "SCHEMA_NAME"), id(rs, "CONTROL_NAME"), id(rs, "TABLE_SCHEMA"),
				id(rs, "TABLE_NAME"), rule(rs), correlation == null || correlation.isBlank() ? null : correlation,
				flag(rs, "ENABLE_FLAG"), flag(rs, "VALID_FLAG"), null, null, rs.getString("REMARKS"));
	}

	/**
	 * Stores explicit permissions and the activation states of the loaded tables in
	 * the schema snapshot; the transient table markers are removed.
	 */
	static void apply(Schema schema, List<Db2RowPermissions.Permission> permissions) {
		var tables = new ArrayList<Db2RowPermissions.AccessControl>();
		for (Table table : schema.getTables()) {
			String control = table.getSpecifics().get(ACCESS_CONTROL);
			if (control == null)
				continue;
			removeSpecific(table, ACCESS_CONTROL);
			if (control.isBlank())
				continue;
			if (!List.of("R", "C", "B").contains(control))
				throw new IllegalArgumentException("Invalid Db2 access-control state: " + control);
			tables.add(new Db2RowPermissions.AccessControl(table.getSchemaName(), table.getName(), !"C".equals(control),
					!"R".equals(control)));
		}
		Db2RowPermissions.set(schema, permissions, tables);
	}

	static void removeSpecific(Table table, String key) {
		var retained = new com.sqlapp.data.schemas.DbInfo();
		table.getSpecifics().forEach((k, v) -> {
			if (!key.equals(k))
				retained.put(k, v);
		});
		table.setSpecifics(retained);
	}

	private static String rule(ExResultSet rs) throws SQLException {
		String value = rs.getString("RULETEXT");
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new SQLException("Missing or truncated access-control rule text");
		return value.strip();
	}

	private static String id(ExResultSet rs, String name) throws SQLException {
		String value = rs.getString(name);
		return value == null ? null : value.stripTrailing();
	}

	private static boolean flag(ExResultSet rs, String name) throws SQLException {
		String value = id(rs, name);
		if (!"Y".equals(value) && !"N".equals(value))
			throw new SQLException("Invalid catalog flag " + name + ": " + value);
		return "Y".equals(value);
	}
}
