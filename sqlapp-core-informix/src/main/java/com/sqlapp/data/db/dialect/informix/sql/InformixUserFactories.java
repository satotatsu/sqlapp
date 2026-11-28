/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.User;

/**
 * Connected-database authorization, independent of OS or mapped account
 * management.
 */
public final class InformixUserFactories {
	private InformixUserFactories() {
	}

	private static String name(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Informix authorization names must be nonempty identifiers");
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String principal(User user) {
		name(user.getName());
		return "public".equalsIgnoreCase(user.getName()) ? "PUBLIC" : name(user.getName());
	}

	private static String role(User user) {
		return user.getSpecifics().get("INFORMIX_DEFAULT_ROLE");
	}

	private static String kind(User user) {
		String kind = user.getSpecifics().getOrDefault("INFORMIX_USER_TYPE", "C");
		if (kind == null || !Set.of("C", "R", "D", "U").contains(kind))
			throw new IllegalArgumentException("INFORMIX_USER_TYPE must be C, R, D or U");
		principal(user);
		if (user.getPassword() != null || user.getLoginUserName() != null || user.getLockedAt() != null
				|| user.getExpiredAt() != null || user.getDefaultSchemaName() != null)
			throw new IllegalArgumentException(
					"Informix database authorization does not manage password, login, lock, expiry or default schema");
		if (user.isAdmin() && !"D".equals(kind))
			throw new IllegalArgumentException("User.admin requires explicit INFORMIX_USER_TYPE=D");
		for (String key : user.getSpecifics().keySet())
			if (!Set.of("INFORMIX_USER_TYPE", "INFORMIX_DEFAULT_ROLE").contains(key))
				throw new IllegalArgumentException("Unsupported Informix user setting: " + key);
		if (role(user) != null) {
			name(role(user));
			if ("public".equalsIgnoreCase(role(user)))
				throw new IllegalArgumentException("PUBLIC cannot be used as an Informix role name");
		}
		if ("U".equals(kind) && role(user) == null)
			throw new IllegalArgumentException("INFORMIX_USER_TYPE=U requires INFORMIX_DEFAULT_ROLE");
		return kind;
	}

	private static String level(String kind) {
		return switch (kind) {
		case "D" -> "DBA";
		case "R" -> "RESOURCE";
		default -> "CONNECT";
		};
	}

	private static void append(List<SqlOperation> result, String sql, SqlType type, User user) {
		result.add(new SqlOperation(sql, type, user));
	}

	private static void grantLevel(List<SqlOperation> result, User user, String kind) {
		if (!"U".equals(kind))
			append(result, "GRANT " + level(kind) + " TO " + principal(user), SqlType.GRANT, user);
	}

	private static void defaultRole(List<SqlOperation> result, User user) {
		append(result, "GRANT DEFAULT ROLE " + name(role(user)) + " TO " + principal(user), SqlType.GRANT, user);
	}

	public static class Grant extends SimpleSqlFactory<User, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(User user) {
			String kind = kind(user);
			List<SqlOperation> result = new ArrayList<>();
			grantLevel(result, user, kind);
			if (role(user) != null)
				defaultRole(result, user);
			return result;
		}
	}

	public static class Revoke extends SimpleSqlFactory<User, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(User user) {
			String kind = kind(user);
			List<SqlOperation> result = new ArrayList<>();
			if (role(user) != null)
				append(result, "REVOKE DEFAULT ROLE FROM " + principal(user), SqlType.REVOKE, user);
			if ("D".equals(kind) || "R".equals(kind))
				append(result, "REVOKE " + level(kind) + " FROM " + principal(user), SqlType.REVOKE, user);
			if (!"U".equals(kind))
				append(result, "REVOKE CONNECT FROM " + principal(user), SqlType.REVOKE, user);
			return result;
		}
	}

	public static class Alter extends SimpleSqlFactory<User, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(User user) {
			throw new IllegalArgumentException("Informix user ALTER requires an original/target difference");
		}

		@Override
		public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
			if (!(difference.getOriginal() instanceof User original)
					|| !(difference.getTarget() instanceof User target))
				throw new IllegalArgumentException("Informix user ALTER requires original and target users");
			String before = kind(original), after = kind(target);
			if (!Objects.equals(original.getName(), target.getName()))
				throw new IllegalArgumentException(
						"Informix database authorization cannot rename authentication accounts");
			if ("U".equals(after) && !"U".equals(before))
				throw new IllegalArgumentException(
						"Conversion to implicit default-role access requires a separately verified CONNECT revocation plan");
			List<SqlOperation> result = new ArrayList<>();
			if (!before.equals(after)) {
				// Revoking DBA/RESOURCE leaves CONNECT. Do not remove it during downgrade.
				if (("D".equals(before) && !"D".equals(after)) || ("R".equals(before) && "C".equals(after)))
					append(result, "REVOKE " + level(before) + " FROM " + principal(original), SqlType.REVOKE,
							original);
				grantLevel(result, target, after);
			}
			if (!Objects.equals(role(original), role(target))) {
				if (role(target) == null)
					append(result, "REVOKE DEFAULT ROLE FROM " + principal(original), SqlType.REVOKE, original);
				else
					defaultRole(result, target);
			}
			return result;
		}
	}
}
