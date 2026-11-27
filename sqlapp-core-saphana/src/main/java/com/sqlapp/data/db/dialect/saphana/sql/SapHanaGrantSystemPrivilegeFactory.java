/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class SapHanaGrantSystemPrivilegeFactory extends SimpleSqlFactory<DbCommonObject<?>, SapHanaSqlBuilder> {
	protected boolean grant() {
		return true;
	}

	@Override
	public java.util.List<SqlOperation> createSql(DbCommonObject<?> privilege) {
		if (!(privilege instanceof UserPrivilege) && !(privilege instanceof RolePrivilege))
			throw new IllegalArgumentException("SAP HANA system privileges require UserPrivilege or RolePrivilege");
		String name = privilege instanceof UserPrivilege user ? user.getPrivilege()
				: ((RolePrivilege) privilege).getPrivilege();
		String grantee = privilege instanceof UserPrivilege user ? user.getGranteeName()
				: ((RolePrivilege) privilege).getGranteeName();
		var state = privilege instanceof UserPrivilege user ? user.getState() : ((RolePrivilege) privilege).getState();
		String valid = privilege instanceof UserPrivilege user ? user.getSpecifics().get("IS_VALID")
				: ((RolePrivilege) privilege).getSpecifics().get("IS_VALID");
		if (grant() && "FALSE".equalsIgnoreCase(valid))
			throw new IllegalArgumentException("Cannot replay an invalid SAP HANA system grant");
		String action = name == null ? "" : name.trim().toUpperCase(java.util.Locale.ROOT);
		if (!action.matches("[A-Z][A-Z_. ]*") || grantee == null || grantee.isBlank())
			throw new IllegalArgumentException("SAP HANA system privilege requires a privilege token and grantee");
		if (state != null && state != (grant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("SAP HANA system privilege state conflicts with GRANT/REVOKE");
		boolean admin = privilege instanceof UserPrivilege user ? user.isAdmin()
				: ((RolePrivilege) privilege).isAdmin();
		var builder = createSqlBuilder()._add(grant() ? "GRANT " : "REVOKE ")._add(action)
				._add(grant() ? " TO " : " FROM ").name(grantee);
		if (grant() && admin)
			builder._add(" WITH ADMIN OPTION");
		var result = new java.util.ArrayList<SqlOperation>();
		addSql(result, builder, grant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
