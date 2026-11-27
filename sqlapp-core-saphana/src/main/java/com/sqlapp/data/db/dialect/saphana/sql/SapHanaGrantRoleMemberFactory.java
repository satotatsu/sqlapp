/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.RoleMember;

public class SapHanaGrantRoleMemberFactory extends SimpleSqlFactory<RoleMember, SapHanaSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(final RoleMember member) {
		if (member.getMemberRoleName() == null || member.getMemberRoleName().isBlank()
				|| member.getGranteeName() == null || member.getGranteeName().isBlank())
			throw new IllegalArgumentException("SAP HANA role membership requires a role and grantee");
		var builder = createSqlBuilder()._add(isGrant() ? "GRANT " : "REVOKE ").name(member.getMemberRoleName())
				._add(isGrant() ? " TO " : " FROM ").name(member.getGranteeName());
		if (isGrant() && member.isAdmin())
			builder._add(" WITH ADMIN OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, member);
		return result;
	}
}
