/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.hsql.util.HsqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.RoleMember;

/**
 * Direct role grants. Administrator/default-role flags are not grant options.
 */
public class HsqlGrantRoleMemberFactory extends SimpleSqlFactory<RoleMember, HsqlSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(RoleMember member) {
		String role = HsqlCreateRoleFactory.identifier(member.getMemberRoleName());
		String grantee = HsqlCreateRoleFactory.identifier(member.getGranteeName());
		if ("PUBLIC".equals(member.getMemberRoleName()) || "_SYSTEM".equals(member.getGranteeName())
				|| HsqlCreateRoleFactory.SYSTEM_ROLES.contains(member.getGranteeName()))
			throw new IllegalArgumentException("HSQLDB manages this implicit/system role membership");
		if (member.isAdmin() || member.isDefault())
			throw new IllegalArgumentException(
					"HSQLDB does not support per-grant ADMIN OPTION or DEFAULT role settings");
		String grantor = member.getSpecifics().get("GRANTOR");
		if (grantor != null && !"_SYSTEM".equals(grantor))
			throw new IllegalArgumentException("HSQLDB role metadata requires the engine's _SYSTEM grantor");
		var sql = createSqlBuilder()._add(isGrant() ? "GRANT " : "REVOKE ")._add(role)
				._add(isGrant() ? " TO " : " FROM ")._add(grantee);
		if (!isGrant())
			sql._add(" RESTRICT");
		var result = new ArrayList<SqlOperation>();
		addSql(result, sql, isGrant() ? SqlType.GRANT : SqlType.REVOKE, member);
		return result;
	}
}
