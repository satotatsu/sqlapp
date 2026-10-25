/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.properties.PrivilegeProperty;
import com.sqlapp.data.schemas.properties.GranteeNameProperty;
import com.sqlapp.data.schemas.properties.GrantableProperty;
import com.sqlapp.data.schemas.properties.PrivilegeStateProperty;

/** Common account quoting and scope validation for native privilege SQL. */
abstract class MySqlPrivilegeFactory<T extends AbstractDbObject<T> & PrivilegeProperty<T> & GranteeNameProperty<T> & GrantableProperty<T> & PrivilegeStateProperty<T>>
		extends SimpleSqlFactory<T, MySqlSqlBuilder> {
	protected static final Set<String> TABLE_PRIVILEGES = Set.of("ALTER", "CREATE", "CREATE VIEW", "DELETE", "DROP",
			"GRANT OPTION", "INDEX", "INSERT", "REFERENCES", "SELECT", "SHOW VIEW", "TRIGGER", "UPDATE");

	protected boolean isGrant() {
		return true;
	}

	protected abstract boolean supportsPrivilege(String privilege);

	protected abstract void appendTarget(T object, MySqlSqlBuilder builder);

	protected void validate(T object) {
	}

	protected void appendColumns(T object, MySqlSqlBuilder builder) {
	}

	@Override
	public List<SqlOperation> createSql(T object) {
		String privilege = object.getPrivilege();
		privilege = privilege == null ? "" : privilege.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (!supportsPrivilege(privilege))
			throw new IllegalArgumentException("Unsupported MySQL privilege for this scope: " + privilege);
		if (object.getState() != null
				&& object.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with requested GRANT/REVOKE");
		validate(object);
		String account = MySqlAccount.sql(object.getGranteeName());
		var builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(privilege);
		appendColumns(object, builder);
		builder._add(" ON ");
		appendTarget(object, builder);
		builder._add(isGrant() ? " TO " : " FROM ")._add(account);
		if (isGrant() && object.isGrantable())
			builder._add(" WITH GRANT OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, object);
		return result;
	}

	protected String identifier(String value, String field) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("MySQL privilege requires " + field);
		return "`" + value.replace("`", "``") + "`";
	}
}
