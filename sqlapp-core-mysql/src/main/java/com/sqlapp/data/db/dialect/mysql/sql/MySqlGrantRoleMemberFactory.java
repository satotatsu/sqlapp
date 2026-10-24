/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.sqlapp.data.db.dialect.mysql.util.MySqlAccount;
import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.RoleMember;

/**
 * Additive role grants. Default-role replacement is an explicit, complete-set
 * operation.
 */
public class MySqlGrantRoleMemberFactory extends SimpleSqlFactory<RoleMember, MySqlSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(RoleMember member) {
		var builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(MySqlAccount.sql(member.getMemberRoleName()))
				._add(isGrant() ? " TO " : " FROM ")._add(MySqlAccount.sql(member.getGranteeName()));
		if (isGrant() && member.isAdmin())
			builder._add(" WITH ADMIN OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, member);
		return result;
	}

	/**
	 * Generates grants followed by one default-role replacement per recipient.
	 * Callers must supply ALL direct memberships for every included recipient;
	 * omitted recipients are untouched. Existing recipient accounts must exist. No
	 * SQL is executed. Use createSql for additive grants without replacing
	 * defaults.
	 */
	public List<SqlOperation> createRestoreSql(Collection<RoleMember> completeMemberships) {
		if (!isGrant())
			throw new IllegalStateException("Role restoration requires the GRANT factory");
		Map<String, Map<String, RoleMember>> recipients = new TreeMap<>();
		for (RoleMember member : completeMemberships) {
			String recipient = MySqlAccount.sql(member.getGranteeName());
			String role = MySqlAccount.sql(member.getMemberRoleName());
			RoleMember previous = recipients.computeIfAbsent(recipient, key -> new TreeMap<>()).putIfAbsent(role,
					member);
			if (previous != null
					&& (previous.isDefault() != member.isDefault() || previous.isAdmin() != member.isAdmin()))
				throw new IllegalArgumentException("Conflicting role membership for " + recipient + ": " + role);
		}
		List<SqlOperation> result = new ArrayList<>();
		for (var roles : recipients.values())
			for (RoleMember member : roles.values())
				result.addAll(createSql(member));
		for (var entry : recipients.entrySet()) {
			var defaults = new TreeSet<String>();
			entry.getValue().forEach((role, member) -> {
				if (member.isDefault())
					defaults.add(role);
			});
			var builder = createSqlBuilder();
			builder._add("SET DEFAULT ROLE ")._add(defaults.isEmpty() ? "NONE" : String.join(", ", defaults))
					._add(" TO ")._add(entry.getKey());
			addSql(result, builder, SqlType.GRANT, entry.getValue().values().iterator().next());
		}
		return result;
	}
}
