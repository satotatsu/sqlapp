/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.UserPrivilege;

/**
 * Principal-type-aware composition; shared privilege matching has no type key.
 */
final class Db2DatabasePrivilegePlan {
	private Db2DatabasePrivilegePlan() {
	}

	private record Id(String action, String grantee, String type) {
	}

	private record Entry(UserPrivilege privilege, List<SqlOperation> sql) {
	}

	record Changes(List<SqlOperation> before, List<SqlOperation> after) {
	}

	private static Map<Id, Entry> effective(Collection<UserPrivilege> privileges, SqlFactoryRegistry registry,
			SqlType type) {
		Map<Id, Entry> result = new LinkedHashMap<>();
		for (UserPrivilege privilege : privileges) {
			if (privilege.getState() != null && privilege.getState() != PrivilegeState.Grant)
				throw new IllegalArgumentException(
						"Db2 database snapshots must describe held grants; remove the authority from the target to revoke it");
			UserPrivilege operation = privilege.clone();
			if (type == SqlType.REVOKE)
				operation.setState(PrivilegeState.Revoke);
			SqlFactory<UserPrivilege> factory = registry.getSqlFactory(operation, type);
			var sql = factory.createSql(operation); // Validate every row before deduplicating grantors.
			String principalType = "PUBLIC".equals(privilege.getGranteeName()) ? "PUBLIC"
					: privilege.getSpecifics().get("GRANTEE_TYPE");
			Id id = new Id(privilege.getPrivilege().toUpperCase(java.util.Locale.ROOT), privilege.getGranteeName(),
					principalType);
			Entry previous = result.putIfAbsent(id, new Entry(privilege, sql));
			if (previous != null && !Objects.equals(previous.privilege().getSpecifics().get("DBADM_MODEL"),
					privilege.getSpecifics().get("DBADM_MODEL")))
				throw new IllegalArgumentException("Conflicting Db2 DBADM source models: " + id);
		}
		return result;
	}

	static List<SqlOperation> create(Collection<UserPrivilege> privileges, SqlFactoryRegistry registry) {
		return effective(privileges, registry, SqlType.GRANT).values().stream().flatMap(e -> e.sql().stream()).toList();
	}

	static Changes diff(Catalog original, Catalog target, SqlFactoryRegistry registry) {
		var before = effective(original.getUserPrivileges(), registry, SqlType.REVOKE);
		var after = effective(target.getUserPrivileges(), registry, SqlType.GRANT);
		List<SqlOperation> revokes = new ArrayList<>(), grants = new ArrayList<>();
		before.entrySet().stream().sorted(java.util.Comparator.comparingInt(e -> revokeOrder(e.getKey().action())))
				.forEach(entry -> {
					Id id = entry.getKey();
					if (!after.containsKey(id)) {
						if (Set.of("BINDADD", "CONNECT", "CREATETAB", "IMPLICIT_SCHEMA", "LOAD", "SQLADM", "WLMADM")
								.contains(id.action()) && after.containsKey(new Id("DBADM", id.grantee(), id.type())))
							throw new IllegalArgumentException(
									"Cannot remove Db2 database authority while retaining DBADM for the same principal: "
											+ id);
						if ("CREATE_EXTERNAL_ROUTINE".equals(id.action())
								&& after.containsKey(new Id("CREATE_NOT_FENCED_ROUTINE", id.grantee(), id.type())))
							throw new IllegalArgumentException(
									"Cannot remove CREATE_EXTERNAL_ROUTINE while retaining CREATE_NOT_FENCED_ROUTINE");
						revokes.addAll(entry.getValue().sql());
					}
				});
		after.forEach((id, entry) -> {
			Entry previous = before.get(id);
			if (previous == null)
				grants.addAll(entry.sql());
			else if (!Objects.equals(previous.privilege().getSpecifics().get("DBADM_MODEL"),
					entry.privilege().getSpecifics().get("DBADM_MODEL")))
				throw new IllegalArgumentException(
						"Changing Db2 DBADM source model requires a target-catalog reread and explicit authority plan");
		});
		return new Changes(List.copyOf(revokes), List.copyOf(grants));
	}

	private static int revokeOrder(String action) {
		return switch (action) {
		case "DBADM" -> 0;
		case "CREATE_NOT_FENCED_ROUTINE" -> 1;
		case "SECADM" -> 100;
		default -> 50;
		};
	}
}
