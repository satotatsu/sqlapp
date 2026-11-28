/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.schemas.Role;
import com.sqlapp.data.schemas.RoleMember;
import com.sqlapp.data.schemas.DbObjectDifference;

/**
 * Database roles and membership; does not create external authentication
 * accounts.
 */
public final class InformixRoleFactories {
	private InformixRoleFactories() {
	}

	private static String name(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Informix role and grantee names must be nonempty identifiers");
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	public static class CreateRole extends SimpleSqlFactory<Role, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Role role) {
			validate(role);
			return List.of(new SqlOperation("CREATE ROLE " + name(role.getName()), SqlType.CREATE, role));
		}
	}

	public static class DropRole extends SimpleSqlFactory<Role, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(Role role) {
			validate(role);
			return List.of(new SqlOperation("DROP ROLE " + name(role.getName()), SqlType.DROP, role));
		}
	}

	private static void validate(Role role) {
		name(role.getName());
		if ("public".equalsIgnoreCase(role.getName()) || role.isGrantable())
			throw new IllegalArgumentException(
					"Informix roles cannot be PUBLIC or carry a role-level grant option; use RoleMember.admin");
	}

	private static String grantee(RoleMember member) {
		name(member.getGranteeName());
		return "public".equalsIgnoreCase(member.getGranteeName()) ? "PUBLIC" : name(member.getGranteeName());
	}

	private static void validate(RoleMember member) {
		validate(new Role(member.getMemberRoleName()));
		grantee(member);
		if (member.getGrantee() instanceof Role && (member.isDefault() || member.isAdmin()))
			throw new IllegalArgumentException("Informix default roles and WITH GRANT OPTION require a user grantee");
		if (member.isAdmin() && "public".equalsIgnoreCase(member.getGranteeName()))
			throw new IllegalArgumentException("Informix WITH GRANT OPTION cannot be granted to PUBLIC");
	}

	public static class GrantRole extends SimpleSqlFactory<RoleMember, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(RoleMember member) {
			validate(member);
			List<SqlOperation> result = new ArrayList<>();
			if (!member.isDefault() || member.isAdmin())
				result.add(new SqlOperation("GRANT " + name(member.getMemberRoleName()) + " TO " + grantee(member)
						+ (member.isAdmin() ? " WITH GRANT OPTION" : ""), SqlType.GRANT, member));
			if (member.isDefault())
				result.add(new SqlOperation(
						"GRANT DEFAULT ROLE " + name(member.getMemberRoleName()) + " TO " + grantee(member),
						SqlType.GRANT, member));
			return result;
		}
	}

	public static class RevokeRole extends SimpleSqlFactory<RoleMember, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(RoleMember member) {
			validate(member);
			List<SqlOperation> result = new ArrayList<>();
			if (member.isDefault())
				result.add(new SqlOperation("REVOKE DEFAULT ROLE FROM " + grantee(member), SqlType.REVOKE, member));
			result.add(new SqlOperation("REVOKE " + name(member.getMemberRoleName()) + " FROM " + grantee(member),
					SqlType.REVOKE, member));
			return result;
		}
	}

	public static class AlterMembership extends SimpleSqlFactory<RoleMember, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(RoleMember member) {
			throw new IllegalArgumentException("Informix membership ALTER requires an original/target difference");
		}

		@Override
		public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
			if (!(difference.getOriginal() instanceof RoleMember before)
					|| !(difference.getTarget() instanceof RoleMember after))
				throw new IllegalArgumentException(
						"Informix membership ALTER requires original and target memberships");
			validate(before);
			validate(after);
			if (!java.util.Objects.equals(before.getMemberRoleName(), after.getMemberRoleName())
					|| !java.util.Objects.equals(before.getGranteeName(), after.getGranteeName()))
				throw new IllegalArgumentException(
						"Membership identity changes require explicit revoke and grant operations");
			if (before.isAdmin() && !after.isAdmin())
				throw new IllegalArgumentException(
						"Removing Informix role grant option can revoke downstream grants; an explicit grant-chain plan is required");
			List<SqlOperation> result = new ArrayList<>();
			if (!before.isAdmin() && after.isAdmin())
				result.addAll(getSqlFactoryRegistry().createSql(after.clone().setDefault(false), SqlType.GRANT));
			if (before.isDefault() != after.isDefault()) {
				if (after.isDefault())
					result.add(new SqlOperation(
							"GRANT DEFAULT ROLE " + name(after.getMemberRoleName()) + " TO " + grantee(after),
							SqlType.GRANT, after));
				else
					result.add(new SqlOperation("REVOKE DEFAULT ROLE FROM " + grantee(before), SqlType.REVOKE, before));
			}
			return result;
		}
	}
}
