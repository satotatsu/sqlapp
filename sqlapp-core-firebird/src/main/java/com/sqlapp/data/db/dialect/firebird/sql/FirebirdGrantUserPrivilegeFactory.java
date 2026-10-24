/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdDdlPrivileges;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.UserPrivilege;

public class FirebirdGrantUserPrivilegeFactory extends SimpleSqlFactory<UserPrivilege, FirebirdSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(UserPrivilege privilege) {
		if (getDialect().compareTo(DialectHolder.defaultDialect30) < 0)
			throw new IllegalArgumentException("DDL privileges require Firebird 3.0 or later");
		String name = FirebirdDdlPrivileges.normalize(privilege.getPrivilege());
		var specifics = new java.util.HashMap<>(privilege.getSpecifics());
		if (!specifics.containsKey(FirebirdPrivilege.GRANTEE_TYPE)
				&& privilege.getGrantee() instanceof com.sqlapp.data.schemas.Role)
			specifics.put(FirebirdPrivilege.GRANTEE_TYPE, "ROLE");
		String recipientType = specifics.getOrDefault(FirebirdPrivilege.GRANTEE_TYPE, "USER");
		if (!Set.of("USER", "ROLE").contains(recipientType))
			throw new IllegalArgumentException("DDL privileges can only be granted to users or roles");
		if (privilege.isAdmin())
			throw new IllegalArgumentException("DDL privileges use WITH GRANT OPTION, not ADMIN OPTION");
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Firebird DDL privilege state conflicts with the requested operation");
		if ("CREATE DATABASE".equals(name) && privilege.isGrantable())
			throw new IllegalArgumentException("CREATE DATABASE does not support WITH GRANT OPTION");
		var builder = createSqlBuilder()._add(isGrant() ? "GRANT " : "REVOKE ");
		if ("CREATE DATABASE".equals(name) && privilege.getSpecifics().containsKey(FirebirdPrivilege.REVOKE_OPTION))
			throw new IllegalArgumentException("CREATE DATABASE does not support GRANT OPTION FOR");
		FirebirdPrivilege.revokeOption(isGrant(), false, privilege.getSpecifics(), builder);
		builder._add(name)._add(isGrant() ? " TO " : " FROM ");
		FirebirdPrivilege.grantee(privilege.getGranteeName(), specifics, builder);
		if (isGrant() && privilege.isGrantable())
			builder._add(" WITH GRANT OPTION");
		FirebirdPrivilege.grantor(privilege.getGrantorName(), builder);
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
