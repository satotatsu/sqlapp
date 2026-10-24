/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
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
		default -> throw new IllegalArgumentException("Db2 routine privilege requires ROUTINE_TYPE F or P");
		};
		if (privilege.getSpecificName() == null || privilege.getSpecificName().isBlank())
			throw new IllegalArgumentException("Db2 routine privilege requires a specific name");
		builder.space().specific().space()._add(keyword).space();
		Db2PrivilegeSql.target(builder, privilege.getSchemaName(), privilege.getSpecificName());
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
