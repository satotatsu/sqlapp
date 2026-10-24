/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.db.dialect.db2.Db2ObjectPrivileges;

public class Db2GrantObjectPrivilegeFactory extends SimpleSqlFactory<ObjectPrivilege, Db2SqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(ObjectPrivilege privilege) {
		String kind = privilege.getSpecifics().get("OBJECT_TYPE");
		if (kind != null && !"TABLE".equals(kind)) {
			if (privilege.getState() != null
					&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
				throw new IllegalArgumentException("Privilege state conflicts with requested Db2 GRANT/REVOKE");
			if (privilege.isHierachy())
				throw new IllegalArgumentException("Db2 sequence/index/server grants do not support hierarchy");
			var grant = Db2ObjectPrivileges.fromModel(privilege);
			List<SqlOperation> result = new ArrayList<>();
			addSql(result,
					createSqlBuilder()._add(
							isGrant() ? Db2ObjectPrivileges.grantSql(grant) : Db2ObjectPrivileges.revokeSql(grant)),
					isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
			return result;
		}
		String action = privilege.getPrivilege() == null ? ""
				: privilege.getPrivilege().toUpperCase(java.util.Locale.ROOT);
		if (!Set.of("CONTROL", "ALTER", "DELETE", "INDEX", "INSERT", "REFERENCES", "SELECT", "UPDATE").contains(action))
			throw new IllegalArgumentException("Unsupported Db2 privilege: " + action);
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with requested Db2 GRANT/REVOKE");
		Db2SqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(action);

		builder.space().on();
		builder.space()._add("TABLE").space();
		Db2PrivilegeSql.target(builder, privilege.getSchemaName(), privilege.getObjectName());
		builder.space()._add(isGrant() ? "TO " : "FROM ");
		Db2PrivilegeSql.grantee(builder, privilege.getGranteeName(), privilege.getSpecifics().get("GRANTEE_TYPE"));
		if (isGrant() && privilege.isGrantable()) {
			if ("CONTROL".equals(action))
				throw new IllegalArgumentException("Db2 CONTROL cannot have WITH GRANT OPTION");
			builder.space()._add("WITH GRANT OPTION");
		}

		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
