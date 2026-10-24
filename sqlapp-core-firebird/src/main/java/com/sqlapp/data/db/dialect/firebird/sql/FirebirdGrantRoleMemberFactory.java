/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.RoleMember;

public class FirebirdGrantRoleMemberFactory extends SimpleSqlFactory<RoleMember, FirebirdSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(RoleMember member) {
		FirebirdPrivilege.identifier(member.getMemberRoleName(), "role name");
		String recipientType = member.getSpecifics().getOrDefault(FirebirdPrivilege.GRANTEE_TYPE,
				member.getGrantee() instanceof com.sqlapp.data.schemas.Role ? "ROLE" : "USER");
		if (!"USER".equals(recipientType) && !"ROLE".equals(recipientType)) {
			throw new IllegalArgumentException("Firebird roles can only be granted to users or roles");
		}
		if ((member.isDefault() || "ROLE".equals(recipientType))
				&& getDialect().compareTo(DialectHolder.defaultDialect40) < 0) {
			throw new IllegalArgumentException("Firebird default and nested roles require Firebird 4.0 or later");
		}
		var builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ");
		FirebirdPrivilege.revokeOption(isGrant(), true, member.getSpecifics(), builder);
		// REVOKE removes the membership, including its default/admin properties.
		if (isGrant() && member.isDefault())
			builder._add("DEFAULT ");
		builder.name(member.getMemberRoleName())._add(isGrant() ? " TO " : " FROM ");
		var specifics = new java.util.HashMap<>(member.getSpecifics());
		specifics.put(FirebirdPrivilege.GRANTEE_TYPE, recipientType);
		FirebirdPrivilege.grantee(member.getGranteeName(), specifics, builder);
		if (isGrant() && member.isAdmin())
			builder._add(" WITH ADMIN OPTION");
		FirebirdPrivilege.grantor(member.getSpecifics().get(FirebirdPrivilege.GRANTOR), builder);
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, member);
		return result;
	}
}
