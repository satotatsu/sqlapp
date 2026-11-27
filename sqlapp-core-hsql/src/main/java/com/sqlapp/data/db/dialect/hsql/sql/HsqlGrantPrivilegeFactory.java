/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.properties.GrantableProperty;
import com.sqlapp.data.schemas.properties.GranteeNameProperty;
import com.sqlapp.data.schemas.properties.ObjectNameProperty;
import com.sqlapp.data.schemas.properties.PrivilegeProperty;
import com.sqlapp.data.schemas.properties.PrivilegeStateProperty;
import com.sqlapp.data.schemas.properties.SchemaNameGetter;

/** HSQLDB object, column and routine-family privileges. */
public class HsqlGrantPrivilegeFactory extends SimpleSqlFactory<DbCommonObject<?>, HsqlSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(DbCommonObject<?> object) {
		if (!(object instanceof ObjectPrivilege || object instanceof ColumnPrivilege
				|| object instanceof RoutinePrivilege)) {
			throw new IllegalArgumentException("An object, column or routine privilege is required");
		}
		String permission = ((PrivilegeProperty<?>) object).getPrivilege();
		String grantee = ((GranteeNameProperty<?>) object).getGranteeName();
		String schema = ((SchemaNameGetter) object).getSchemaName();
		String name = ((ObjectNameProperty<?>) object).getObjectName();
		boolean grantable = ((GrantableProperty<?>) object).isGrantable();
		var state = ((PrivilegeStateProperty<?>) object).getState();
		if (state != null && state != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with GRANT/REVOKE");
		if (permission == null || permission.isBlank() || grantee == null || grantee.isBlank() || name == null
				|| name.isBlank()) {
			throw new IllegalArgumentException("Privilege, object identity and grantee are required");
		}
		permission = permission.toUpperCase(Locale.ROOT);
		if (Set.of("_SYSTEM", "DBA", "CREATE_SCHEMA", "CHANGE_AUTHORIZATION", "SCRIPT_OPS").contains(grantee)) {
			throw new IllegalArgumentException("HSQLDB manages privileges for this system principal: " + grantee);
		}
		if ("PUBLIC".equals(grantee) && grantable && isGrant())
			throw new IllegalArgumentException("PUBLIC cannot receive WITH GRANT OPTION");
		String kind = "TABLE";
		String filterScope = object instanceof ObjectPrivilege p ? p.getSpecifics().get("hsql.privilegeFilterScope.v1")
				: object instanceof ColumnPrivilege p ? p.getSpecifics().get("hsql.privilegeFilterScope.v1") : null;
		if (filterScope != null && !"UNFILTERED".equals(filterScope)) {
			throw new IllegalArgumentException(
					"Filtered or unverified HSQLDB privilege metadata requires a separate privilege migration");
		}
		String column = null;
		Set<String> allowed = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "REFERENCES", "TRIGGER");
		if (object instanceof ColumnPrivilege privilege) {
			column = privilege.getColumnName();
			if (column == null || column.isBlank())
				throw new IllegalArgumentException("Column privilege requires a column name");
			allowed = Set.of("SELECT", "INSERT", "UPDATE", "REFERENCES");
		} else if (object instanceof RoutinePrivilege privilege) {
			if (privilege.isHierachy())
				throw new IllegalArgumentException("HSQLDB does not support hierarchy grants");
			if (!"FAMILY".equals(privilege.getSpecifics().get("hsql.routineGrantScope.v1"))) {
				throw new IllegalArgumentException(
						"HSQLDB 2.7.4 cannot reliably grant individual overloads; require a complete routine family or explicit FAMILY scope in hsql.routineGrantScope.v1");
			}
			kind = "ROUTINE";
			allowed = Set.of("EXECUTE");
		} else if (object instanceof ObjectPrivilege privilege) {
			if (privilege.isHierachy())
				throw new IllegalArgumentException("HSQLDB does not support hierarchy grants");
			String type = privilege.getSpecifics().get("OBJECT_TYPE");
			if (type != null)
				kind = type.toUpperCase(Locale.ROOT);
			if (!"TABLE".equals(kind)) {
				if (!Set.of("DOMAIN", "TYPE", "SEQUENCE", "CHARACTER SET").contains(kind))
					throw new IllegalArgumentException("Unsupported HSQLDB privilege object type: " + kind);
				allowed = Set.of("USAGE");
			}
		}
		if (!allowed.contains(permission))
			throw new IllegalArgumentException("Unsupported HSQLDB " + kind + " privilege: " + permission);
		if ("TRIGGER".equals(permission)) {
			throw new IllegalArgumentException(
					"HSQLDB 2.7.4 does not retain explicit TRIGGER grants; separate privilege migration is required");
		}
		var sql = createSqlBuilder()._add(isGrant() ? "GRANT " : "REVOKE ")._add(permission);
		if (column != null)
			sql._add(" (")._add(quote(column))._add(")");
		sql._add(" ON ")._add(kind)._add(" ");
		if (schema != null)
			sql._add(quote(schema))._add(".");
		sql._add(quote(name))._add(isGrant() ? " TO " : " FROM ")
				._add("PUBLIC".equals(grantee) ? "PUBLIC" : quote(grantee));
		if (isGrant() && grantable)
			sql._add(" WITH GRANT OPTION");
		if (!isGrant())
			sql._add(" RESTRICT");
		List<SqlOperation> operations = new ArrayList<>();
		addSql(operations, sql, isGrant() ? SqlType.GRANT : SqlType.REVOKE, object);
		return operations;
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
