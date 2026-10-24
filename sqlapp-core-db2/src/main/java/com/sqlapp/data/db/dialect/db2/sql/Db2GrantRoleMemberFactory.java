/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.RoleMember;

public class Db2GrantRoleMemberFactory extends SimpleSqlFactory<RoleMember, Db2SqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(RoleMember member) {
		if (member.getMemberRoleName() == null || member.getMemberRoleName().isBlank()) {
			throw new IllegalArgumentException("Db2 role membership requires a role name");
		}
		Db2SqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT ROLE " : "REVOKE ROLE ").name(member.getMemberRoleName());
		builder._add(isGrant() ? " TO " : " FROM ");
		Db2PrivilegeSql.grantee(builder, member.getGranteeName(), member.getSpecifics().get("GRANTEE_TYPE"));
		if (isGrant() && member.isAdmin())
			builder._add(" WITH ADMIN OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, member);
		return result;
	}
}
