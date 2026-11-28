/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ObjectPrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.exceptions.SqlappException;

public class SpannerObjectPrivilegeReader extends ObjectPrivilegeReader {
	public SpannerObjectPrivilegeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<ObjectPrivilege> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<ObjectPrivilege> grants = new ArrayList<>();
		readPrivileges(connection, getObjectName(context), grants, "TABLE_PRIVILEGES", "TABLE", "TABLE");
		readPrivileges(connection, getObjectName(context), grants, "CHANGE_STREAM_PRIVILEGES", "CHANGE_STREAM",
				"CHANGE STREAM");
		readPrivileges(connection, getObjectName(context), grants, "ROUTINE_PRIVILEGES", "SPECIFIC", "TABLE FUNCTION");
		return grants;
	}

	private void readPrivileges(final Connection connection, final String objectFilter,
			final List<ObjectPrivilege> grants, final String view, final String prefix, final String kind) {
		final String schemaColumn = prefix + "_SCHEMA";
		final String nameColumn = prefix + "_NAME";
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT " + schemaColumn + ", " + nameColumn
						+ ", PRIVILEGE_TYPE, GRANTEE FROM INFORMATION_SCHEMA." + view + " ORDER BY " + schemaColumn
						+ ", " + nameColumn + ", PRIVILEGE_TYPE, GRANTEE")) {
			while (rows.next()) {
				final String schema = rows.getString(1);
				final String table = rows.getString(2);
				if ((getSchemaName() != null && !getSchemaName().equals(schema))
						|| (objectFilter != null && !objectFilter.equals(table)))
					continue;
				grants.add(toPrivilege(schema, table, rows.getString(3), rows.getString(4), kind));
			}
		} catch (SQLException exception) {
			if (!SpannerRoleReader.isMissingCatalogTable(exception, view))
				throw new SqlappException("Unable to read Spanner privileges from " + view, exception);
		}
	}

	static ObjectPrivilege toPrivilege(final String schema, final String table, final String privilege,
			final String grantee) {
		return toPrivilege(schema, table, privilege, grantee, "TABLE");
	}

	static ObjectPrivilege toPrivilege(final String schema, final String table, final String privilege,
			final String grantee, final String kind) {
		final var supported = switch (kind) {
		case "CHANGE STREAM" -> List.of("SELECT");
		case "TABLE FUNCTION" -> List.of("EXECUTE");
		default -> List.of("SELECT", "INSERT", "UPDATE", "DELETE");
		};
		if (schema == null || table == null || table.isBlank() || grantee == null || grantee.isBlank()
				|| privilege == null || !supported.contains(privilege))
			throw new IllegalArgumentException("Invalid Spanner privilege metadata for " + kind);
		final var grant = new ObjectPrivilege().setSchemaName(schema).setObjectName(table).setHierachy(false)
				.setPrivilege(privilege).setGrantee(new Role(grantee));
		grant.getSpecifics().put(com.sqlapp.data.db.dialect.spanner.sql.SpannerGrantObjectPrivilegeFactory.OBJECT_TYPE,
				kind);
		return grant;
	}
}
