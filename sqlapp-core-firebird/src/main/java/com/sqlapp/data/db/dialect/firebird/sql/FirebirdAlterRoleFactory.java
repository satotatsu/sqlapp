/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSystemPrivileges;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Role;

/** Replaces system privileges without dropping the role or its memberships. */
public class FirebirdAlterRoleFactory extends SimpleSqlFactory<Role, FirebirdSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Role role) {
		if (getDialect().compareTo(DialectHolder.defaultDialect40) < 0)
			throw new IllegalArgumentException("Role system privileges require Firebird 4.0 or later");
		FirebirdPrivilege.identifier(role.getName(), "role name");
		String privileges = FirebirdSystemPrivileges
				.normalize(role.getSpecifics().get(FirebirdSystemPrivileges.ROLE_PRIVILEGES), getDialect());
		var builder = createSqlBuilder()._add("ALTER ROLE ").name(role.getName());
		if (privileges.isEmpty())
			builder._add(" DROP SYSTEM PRIVILEGES");
		else
			builder._add(" SET SYSTEM PRIVILEGES TO ")._add(privileges);
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.ALTER, role);
		return result;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (!(difference.getOriginal() instanceof Role original) || !(difference.getTarget() instanceof Role target))
			throw new IllegalArgumentException("Role ALTER requires original and target roles");
		var changed = difference.getChangedProperties();
		var unsupported = changed.keySet().stream().filter(key -> !Set.of("specifics", "remarks").contains(key))
				.toList();
		var originalSpecifics = new HashMap<>(original.getSpecifics());
		var targetSpecifics = new HashMap<>(target.getSpecifics());
		originalSpecifics.remove(FirebirdSystemPrivileges.ROLE_PRIVILEGES);
		targetSpecifics.remove(FirebirdSystemPrivileges.ROLE_PRIVILEGES);
		if (!unsupported.isEmpty() || !originalSpecifics.equals(targetSpecifics))
			throw new UnsupportedOperationException(
					"Firebird role ALTER supports only system privileges and comments: " + changed.keySet());
		List<SqlOperation> result = new ArrayList<>();
		if (!Objects.equals(original.getSpecifics().get(FirebirdSystemPrivileges.ROLE_PRIVILEGES),
				target.getSpecifics().get(FirebirdSystemPrivileges.ROLE_PRIVILEGES)))
			result.addAll(createSql(target));
		if (changed.containsKey("remarks")) {
			if (getDialect().compareTo(DialectHolder.defaultDialect20) < 0)
				throw new IllegalArgumentException("Role comments require Firebird 2.0 or later");
			var builder = createSqlBuilder()._add("COMMENT ON ROLE ").name(target.getName())._add(" IS ");
			if (target.getRemarks() == null)
				builder._add("NULL");
			else
				builder.sqlChar(target.getRemarks());
			addSql(result, builder, SqlType.SET_COMMENT, target);
		}
		return result;
	}
}
