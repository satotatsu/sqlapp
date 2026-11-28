/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.Role;

/**
 * Informix relation grants. Column revocation cannot safely operate on one
 * column.
 */
public final class InformixTablePrivilegeFactories {
	private InformixTablePrivilegeFactories() {
	}

	private static String name(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"Informix privilege requires nonempty owner, object, column and grantee identifiers");
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String sql(String schema, String object, String column, String privilege, String specific,
			String grantee, String grantor, boolean grantable, boolean role, PrivilegeState state, boolean grant) {
		String keyword = privilege == null ? "" : privilege.toUpperCase(Locale.ROOT);
		Set<String> allowed = column == null
				? Set.of("SELECT", "UPDATE", "INSERT", "DELETE", "REFERENCES", "INDEX", "ALTER", "UNDER")
				: Set.of("SELECT", "UPDATE", "REFERENCES");
		if (!allowed.contains(keyword) || (state != null && state != PrivilegeState.Grant)
				|| (specific != null && !specific.isEmpty()))
			throw new IllegalArgumentException("Unsupported Informix table/column privilege or privilege state");
		String recipient = "public".equalsIgnoreCase(grantee) ? "PUBLIC" : name(grantee);
		if (grantable && (role || "PUBLIC".equals(recipient)))
			throw new IllegalArgumentException("Informix WITH GRANT OPTION requires a user grantee");
		String result = (grant ? "GRANT " : "REVOKE ") + keyword + (column == null ? "" : " (" + name(column) + ")")
				+ " ON " + name(schema) + "." + name(object) + (grant ? " TO " : " FROM ") + recipient;
		if (grant && grantable)
			result += " WITH GRANT OPTION";
		if (grantor != null)
			result += " AS " + name(grantor);
		return result;
	}

	private static String sql(ObjectPrivilege value, boolean grant) {
		if (value.isHierachy())
			throw new IllegalArgumentException("Informix table privileges do not support WITH HIERARCHY");
		return sql(value.getSchemaName(), value.getObjectName(), null, value.getPrivilege(), value.getSpecificName(),
				value.getGranteeName(), value.getGrantorName(), value.isGrantable(), value.getGrantee() instanceof Role,
				value.getState(), grant);
	}

	public static class GrantTable extends SimpleSqlFactory<ObjectPrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(ObjectPrivilege value) {
			return List.of(new SqlOperation(sql(value, true), SqlType.GRANT, value));
		}
	}

	public static class RevokeTable extends SimpleSqlFactory<ObjectPrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(ObjectPrivilege value) {
			return List.of(new SqlOperation(sql(value, false), SqlType.REVOKE, value));
		}
	}

	public static class GrantColumn extends SimpleSqlFactory<ColumnPrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(ColumnPrivilege value) {
			name(value.getColumnName());
			String text = sql(value.getSchemaName(), value.getObjectName(), value.getColumnName(), value.getPrivilege(),
					value.getSpecificName(), value.getGranteeName(), value.getGrantorName(), value.isGrantable(),
					value.getGrantee() instanceof Role, value.getState(), true);
			return List.of(new SqlOperation(text, SqlType.GRANT, value));
		}
	}

	public static class RevokeColumn extends SimpleSqlFactory<ColumnPrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(ColumnPrivilege value) {
			throw new IllegalArgumentException(
					"Informix revokes SELECT/UPDATE/REFERENCES for all columns; a complete table privilege and grant-chain plan is required to revoke one column");
		}
	}
}
