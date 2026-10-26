/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.PrivilegeState;

public class Db2GrantRoutinePrivilegeFactory extends SimpleSqlFactory<RoutinePrivilege, Db2SqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(RoutinePrivilege privilege) {
		String action = privilege.getPrivilege() == null ? ""
				: privilege.getPrivilege().toUpperCase(java.util.Locale.ROOT);
		if (!Set.of("EXECUTE").contains(action))
			throw new IllegalArgumentException("Unsupported Db2 privilege: " + action);
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with requested Db2 GRANT/REVOKE");
		Db2SqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(action);

		builder.space().on();
		String routineType = privilege.getSpecifics().get("ROUTINE_TYPE");
		String keyword = switch (routineType == null ? "" : routineType) {
		case "F" -> "FUNCTION";
		case "P" -> "PROCEDURE";
		case "M" -> "METHOD";
		default -> throw new IllegalArgumentException("Db2 routine privilege requires ROUTINE_TYPE F, M or P");
		};
		String scope = privilege.getSpecifics().get("DB2_ROUTINE_SCOPE");
		if (scope == null) {
			if (privilege.getSpecificName() == null || privilege.getSpecificName().isBlank())
				throw new IllegalArgumentException(
						"Db2 routine privilege requires a specific name or explicit future-object scope");
			builder.space().specific().space()._add(keyword).space();
			if ("M".equals(routineType))
				builder._add(
						privilege.getSchemaName() == null ? Db2CreationContext.identifier(privilege.getSpecificName())
								: Db2CreationContext.qualified(privilege.getSchemaName(), privilege.getSpecificName()));
			else
				Db2PrivilegeSql.target(builder, privilege.getSchemaName(), privilege.getSpecificName());
		} else {
			if (!(getDialect() instanceof com.sqlapp.data.db.dialect.db2.Db2_1110))
				throw new IllegalArgumentException("Db2 future routine privilege scopes require LUW 11.1 or later");
			if (privilege.getSpecificName() != null || privilege.getSchemaName() == null)
				throw new IllegalArgumentException("Db2 future routine scope conflicts with specific identity");
			builder.space()._add(keyword).space();
			if ("M".equals(routineType)) {
				builder._add("* FOR ");
				if ("TYPE".equals(scope))
					builder._add(Db2CreationContext.qualified(privilege.getSpecifics().get("DB2_SUBJECT_TYPE_SCHEMA"),
							privilege.getSpecifics().get("DB2_SUBJECT_TYPE_NAME")));
				else if ("SCHEMA".equals(scope))
					builder._add(Db2CreationContext.identifier(privilege.getSchemaName()))._add(".*");
				else
					throw new IllegalArgumentException("Unsupported Db2 method privilege scope: " + scope);
			} else if ("SCHEMA".equals(scope))
				builder._add(Db2CreationContext.identifier(privilege.getSchemaName()))._add(".*");
			else
				throw new IllegalArgumentException("Unsupported Db2 routine privilege scope: " + scope);
		}
		builder.space()._add(isGrant() ? "TO " : "FROM ");
		Db2PrivilegeSql.grantee(builder, privilege.getGranteeName(), privilege.getSpecifics().get("GRANTEE_TYPE"));
		if (isGrant() && privilege.isGrantable()) {
			if ("CONTROL".equals(action))
				throw new IllegalArgumentException("Db2 CONTROL cannot have WITH GRANT OPTION");
			builder.space()._add("WITH GRANT OPTION");
		}
		if (!isGrant())
			builder.space()._add("RESTRICT");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
