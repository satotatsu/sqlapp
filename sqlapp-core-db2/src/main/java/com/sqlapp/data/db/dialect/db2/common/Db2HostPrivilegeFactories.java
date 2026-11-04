/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

/** Host GRANT grammar differs from LUW USER/GROUP/ROLE clauses. */
public final class Db2HostPrivilegeFactories {
	private Db2HostPrivilegeFactories() {
	}

	public static class GrantObject extends SimpleSqlFactory<ObjectPrivilege, Db2SqlBuilder> {
		protected boolean grant() {
			return true;
		}

		@Override
		public List<SqlOperation> createSql(ObjectPrivilege privilege) {
			if (privilege.isHierachy())
				throw new IllegalArgumentException("Host table privileges do not support hierarchy");
			checkState(privilege.getState(), grant());
			var result = new java.util.ArrayList<SqlOperation>();
			var builder = createSqlBuilder();
			render(builder, grant(), privilege.getPrivilege(), privilege.getSchemaName(), privilege.getObjectName(),
					null, privilege.getGranteeName(), privilege.isGrantable(), privilege.getSpecifics());
			addSql(result, builder, grant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
			return result;
		}
	}

	public static class RevokeObject extends GrantObject {
		@Override
		protected boolean grant() {
			return false;
		}
	}

	public static class GrantColumn extends SimpleSqlFactory<ColumnPrivilege, Db2SqlBuilder> {
		protected boolean grant() {
			return true;
		}

		@Override
		public List<SqlOperation> createSql(ColumnPrivilege privilege) {
			checkState(privilege.getState(), grant());
			if (privilege.getColumnName() == null || privilege.getColumnName().isBlank())
				throw new IllegalArgumentException("Host column grant requires a column name");
			var result = new java.util.ArrayList<SqlOperation>();
			var builder = createSqlBuilder();
			render(builder, grant(), privilege.getPrivilege(), privilege.getSchemaName(), privilege.getObjectName(),
					privilege.getColumnName(), privilege.getGranteeName(), privilege.isGrantable(),
					privilege.getSpecifics());
			addSql(result, builder, grant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
			return result;
		}
	}

	public static class RevokeColumn extends GrantColumn {
		@Override
		protected boolean grant() {
			return false;
		}
	}

	public static class GrantRoutine extends SimpleSqlFactory<RoutinePrivilege, Db2SqlBuilder> {
		protected boolean grant() {
			return true;
		}

		@Override
		public List<SqlOperation> createSql(RoutinePrivilege privilege) {
			checkState(privilege.getState(), grant());
			var fields = privilege.getSpecifics();
			if (privilege.isHierachy() || !"OBJECT".equals(fields.get("DB2_HOST_ROUTINE_SCOPE"))
					|| fields.containsKey("DB2_HOST_ROUTINE_UNRESOLVED"))
				throw new IllegalArgumentException(
						"Host routine schema scopes/unresolved routines require explicit resolution");
			boolean zos = "db2-zos".equals(getDialect().getSimpleName());
			String kind = fields.get("DB2_HOST_ROUTINE_KIND"), action = privilege.getPrivilege();
			if (!Set.of("F", "P").contains(kind == null ? "" : kind)
					|| !("EXECUTE".equals(action) || !zos && "ALTER".equals(action)))
				throw new IllegalArgumentException("Unsupported host routine privilege kind/action");
			String specific = privilege.getSpecificName();
			if (specific == null || specific.isBlank() || "*".equals(specific))
				throw new IllegalArgumentException("Host routine privilege requires an individual specific name");
			String target = com.sqlapp.data.db.dialect.db2.util.Db2CreationContext.qualified(privilege.getSchemaName(),
					zos && "P".equals(kind) ? privilege.getObjectName() : specific);
			var builder = createSqlBuilder();
			builder._add(grant() ? "GRANT " : "REVOKE ")._add(action)._add(" ON ");
			if (!(zos && "P".equals(kind)))
				builder._add("SPECIFIC ");
			builder._add("F".equals(kind) ? "FUNCTION " : "PROCEDURE ")._add(target)._add(grant() ? " TO " : " FROM ");
			grantee(builder, privilege.getGranteeName(), fields);
			if (grant() && privilege.isGrantable())
				builder._add(" WITH GRANT OPTION");
			if (!grant())
				builder._add(" RESTRICT");
			var result = new java.util.ArrayList<SqlOperation>();
			addSql(result, builder, grant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
			return result;
		}
	}

	public static class RevokeRoutine extends GrantRoutine {
		@Override
		protected boolean grant() {
			return false;
		}
	}

	private static void checkState(PrivilegeState state, boolean grant) {
		if (state != null && state != (grant ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Host privilege state conflicts with GRANT/REVOKE");
	}

	private static void render(Db2SqlBuilder builder, boolean grant, String action, String schema, String object,
			String column, String grantee, boolean grantable, Map<String, String> specifics) {
		boolean zos = "db2-zos".equals(builder.getDialect().getSimpleName());
		if (action == null || object == null || object.isBlank() || grantee == null || grantee.isBlank())
			throw new IllegalArgumentException("Host privilege requires action, object and grantee");
		action = action.toUpperCase(java.util.Locale.ROOT);
		Set<String> actions = column != null ? Set.of("UPDATE", "REFERENCES")
				: zos ? Set.of("ALTER", "DELETE", "INDEX", "INSERT", "REFERENCES", "SELECT", "UPDATE", "TRIGGER",
						"UNLOAD") : Set.of("ALTER", "DELETE", "INDEX", "INSERT", "REFERENCES", "SELECT", "UPDATE");
		if (!actions.contains(action))
			throw new IllegalArgumentException("Unsupported host privilege: " + action);
		builder._add(grant ? "GRANT " : "REVOKE ")._add(action);
		if (column != null)
			builder._add(" (").name(column)._add(")");
		builder._add(" ON TABLE ");
		if (schema != null)
			builder.name(schema)._add(".");
		builder.name(object)._add(grant ? " TO " : " FROM ");
		grantee(builder, grantee, specifics);
		if (grant && grantable)
			builder._add(" WITH GRANT OPTION");
	}

	private static void grantee(Db2SqlBuilder builder, String grantee, Map<String, String> specifics) {
		if (specifics.containsKey("DB2_HOST_AUTHORIZATION_LIST")
				|| specifics.containsKey("DB2_HOST_GRANTABILITY_UNKNOWN"))
			throw new IllegalArgumentException(
					"Host authorization-list/unknown grantability requires explicit resolution before SQL generation");
		if (grantee == null || grantee.isBlank())
			throw new IllegalArgumentException("Host privilege requires a grantee");
		boolean zos = "db2-zos".equals(builder.getDialect().getSimpleName());
		String type = specifics.get("DB2_HOST_GRANTEE_TYPE");
		if (type == null || (!"U".equals(type) && !(zos && "R".equals(type))))
			throw new IllegalArgumentException("Unsupported/unresolved host grantee type: " + type);
		if ("PUBLIC".equals(grantee) && "U".equals(type))
			builder._add("PUBLIC");
		else {
			if ("R".equals(type))
				builder._add("ROLE ");
			builder.name(grantee);
		}
	}
}
