/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.User;

/** Password-authenticated users; missing credentials are never invented. */
public class SapHanaCreateUserFactory extends SimpleSqlFactory<User, SapHanaSqlBuilder> {
	protected SqlType operation() {
		return SqlType.CREATE;
	}

	@Override
	public List<SqlOperation> createSql(User user) {
		if (user.getName() == null || user.getName().isBlank())
			throw new IllegalArgumentException("SAP HANA user name is required");
		var result = new ArrayList<SqlOperation>();
		if (operation() == SqlType.DROP) {
			addSql(result, createSqlBuilder()._add("DROP USER ").name(user.getName()), SqlType.DROP, user);
			return result;
		}
		if (user.isAdmin() || user.getLoginUserName() != null || user.getLockedAt() != null
				|| user.getExpiredAt() != null)
			throw new IllegalArgumentException(
					"SAP HANA admin/external identity/lock/expiry settings require explicit user-management DDL");
		if (user.getDefaultSchemaName() != null && !user.getDefaultSchemaName().equals(user.getName()))
			throw new IllegalArgumentException("SAP HANA user's default schema must match its name");
		if (Boolean.FALSE.equals(flag(user, "IS_PASSWORD_ENABLED")))
			throw new IllegalArgumentException(
					"SAP HANA external authentication requires explicit DDL; a password cannot reproduce it");
		if (operation() == SqlType.ALTER && user.getSpecifics().containsKey("IS_RESTRICTED"))
			throw new IllegalArgumentException(
					"SAP HANA restricted-user changes require explicit DDL; use a diff for unchanged metadata");
		String password = user.getPassword();
		boolean supplied = password != null && !password.isEmpty();
		if (operation() == SqlType.CREATE && !supplied)
			throw new IllegalArgumentException(
					"SAP HANA CREATE USER requires an explicit password; catalog metadata does not contain reusable credentials");
		Boolean force = flag(user, "PASSWORD_CHANGE_NEEDED");
		if (Boolean.FALSE.equals(force) && !supplied)
			throw new IllegalArgumentException(
					"Clearing SAP HANA password-change requirement requires an explicit new password");
		if (operation() == SqlType.CREATE) {
			var builder = createSqlBuilder()
					._add(Boolean.TRUE.equals(flag(user, "IS_RESTRICTED")) ? "CREATE RESTRICTED USER " : "CREATE USER ")
					.name(user.getName());
			password(builder, password, force);
			addSql(result, builder, SqlType.CREATE, user);
		}
		Boolean lifetime = flag(user, "PASSWORD_LIFETIME_CHECK_ENABLED");
		if (lifetime != null && (operation() == SqlType.ALTER || !lifetime))
			addAlter(result, user, lifetime ? " ENABLE PASSWORD LIFETIME" : " DISABLE PASSWORD LIFETIME");
		if (operation() == SqlType.ALTER && supplied) {
			var builder = createSqlBuilder()._add("ALTER USER ").name(user.getName());
			password(builder, password, force);
			addSql(result, builder, SqlType.ALTER, user);
		} else if (Boolean.TRUE.equals(force))
			addAlter(result, user, " FORCE PASSWORD CHANGE");
		Boolean deactivated = flag(user, "USER_DEACTIVATED");
		if (deactivated != null && (operation() == SqlType.ALTER || deactivated))
			addAlter(result, user, deactivated ? " DEACTIVATE USER NOW" : " ACTIVATE USER NOW");
		return result;
	}

	private void password(SapHanaSqlBuilder builder, String password, Boolean force) {
		if (password.indexOf('\0') >= 0 || password.indexOf('\r') >= 0 || password.indexOf('\n') >= 0)
			throw new IllegalArgumentException("SAP HANA password cannot contain NUL or line breaks");
		builder._add(" PASSWORD \"")._add(password.replace("\"", "\"\""))._add("\"");
		if (Boolean.FALSE.equals(force))
			builder._add(" NO FORCE_FIRST_PASSWORD_CHANGE");
	}

	private void addAlter(List<SqlOperation> result, User user, String suffix) {
		addSql(result, createSqlBuilder()._add("ALTER USER ").name(user.getName())._add(suffix), SqlType.ALTER, user);
	}

	static Boolean flag(User user, String key) {
		String value = user.getSpecifics().get(key);
		if (value == null)
			return null;
		return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
		case "TRUE", "ON" -> true;
		case "FALSE", "OFF" -> false;
		default -> throw new IllegalArgumentException("SAP HANA user " + key + " must be TRUE/FALSE or ON/OFF");
		};
	}
}
