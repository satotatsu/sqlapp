/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ColumnPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.exceptions.SqlappException;

public class SpannerColumnPrivilegeReader extends ColumnPrivilegeReader {
	public SpannerColumnPrivilegeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ColumnPrivilege> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final String tableFilter = getTableName(context);
		final List<ColumnPrivilege> grants = new ArrayList<>();
		try (var statement = connection.createStatement();
				var rows = statement
						.executeQuery("SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, PRIVILEGE_TYPE, GRANTEE "
								+ "FROM INFORMATION_SCHEMA.COLUMN_PRIVILEGES ORDER BY TABLE_SCHEMA, TABLE_NAME, "
								+ "COLUMN_NAME, PRIVILEGE_TYPE, GRANTEE")) {
			while (rows.next()) {
				final String schema = rows.getString(1);
				final String table = rows.getString(2);
				if ((getSchemaName() != null && !getSchemaName().equals(schema))
						|| (tableFilter != null && !tableFilter.equals(table))
						|| (getColumnName(context) != null && !getColumnName(context).equals(rows.getString(3))))
					continue;
				grants.add(toPrivilege(schema, table, rows.getString(3), rows.getString(4), rows.getString(5)));
			}
		} catch (SQLException exception) {
			if (SpannerRoleReader.isMissingCatalogTable(exception, "COLUMN_PRIVILEGES"))
				return List.of();
			throw new SqlappException("Unable to read Spanner column privileges", exception);
		}
		return grants;
	}

	static ColumnPrivilege toPrivilege(final String schema, final String table, final String column,
			final String privilege, final String grantee) {
		if (schema == null || table == null || table.isBlank() || grantee == null || grantee.isBlank() || column == null
				|| column.isBlank()
				|| !List.of("SELECT", "INSERT", "UPDATE").contains(privilege == null ? "" : privilege))
			throw new IllegalArgumentException("Invalid Spanner column privilege metadata");
		final var grant = new ColumnPrivilege().setSchemaName(schema).setTableName(table).setColumnName(column)
				.setPrivilege(privilege).setGrantee(new Role(grantee));
		return grant;
	}
}
