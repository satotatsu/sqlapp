/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.ColumnPrivilege;
import com.sqlapp.data.db.sql.SqlType;
import java.util.Locale;

final class InformixGrantOrder {
	private record Scope(String owner, String kind, String object, String detail, String privilege) {
	}

	private record GrantKey(Scope scope, String grantee) {
	}

	private record Grant(Scope scope, String grantor, String grantee, boolean grantable) {
	}

	private InformixGrantOrder() {
	}

	static boolean isObjectGrant(SqlOperation operation) {
		return grant(operation) != null;
	}

	static List<SqlOperation> order(Iterable<SqlOperation> operations) {
		Map<GrantKey, List<SqlOperation>> parents = new HashMap<>();
		for (SqlOperation operation : operations) {
			Grant grant = grant(operation);
			if (grant != null && grant.grantable())
				parents.computeIfAbsent(new GrantKey(grant.scope(), grant.grantee()), ignored -> new ArrayList<>())
						.add(operation);
		}
		List<SqlOperation> result = new ArrayList<>();
		Set<SqlOperation> visiting = new HashSet<>(), visited = new HashSet<>();
		for (SqlOperation operation : operations)
			append(operation, parents, visiting, visited, result);
		return result;
	}

	private static Grant grant(SqlOperation operation) {
		if (operation.getSqlType() != SqlType.GRANT)
			return null;
		if (operation.getOriginal() instanceof RoutinePrivilege grant) {
			var scope = new Scope(grant.getSchemaName(), grant.getSpecifics().get("INFORMIX_ROUTINE_KIND"),
					grant.getSpecificName(), grant.getSpecifics().get("INFORMIX_ROUTINE_IDENTITY"), "EXECUTE");
			return new Grant(scope, grant.getGrantorName(), grant.getGranteeName(), grant.isGrantable());
		}
		if (operation.getOriginal() instanceof ObjectPrivilege grant) {
			var scope = new Scope(grant.getSchemaName(), "TABLE", grant.getObjectName(), null,
					grant.getPrivilege().toUpperCase(Locale.ROOT));
			return new Grant(scope, grant.getGrantorName(), grant.getGranteeName(), grant.isGrantable());
		}
		if (operation.getOriginal() instanceof ColumnPrivilege grant) {
			var scope = new Scope(grant.getSchemaName(), "TABLE", grant.getTableName(), grant.getColumnName(),
					grant.getPrivilege().toUpperCase(Locale.ROOT));
			return new Grant(scope, grant.getGrantorName(), grant.getGranteeName(), grant.isGrantable());
		}
		return null;
	}

	private static void append(SqlOperation operation, Map<GrantKey, List<SqlOperation>> parents,
			Set<SqlOperation> visiting, Set<SqlOperation> visited, List<SqlOperation> result) {
		if (visited.contains(operation))
			return;
		if (!visiting.add(operation))
			throw new UnsupportedOperationException("Cyclic Informix grants require an explicit grant plan");
		Grant grant = grant(operation);
		if (grant != null && grant.grantor() != null && !Objects.equals(grant.grantor(), grant.scope().owner())) {
			Scope scope = grant.scope();
			for (SqlOperation parent : parents.getOrDefault(new GrantKey(scope, grant.grantor()), List.of()))
				append(parent, parents, visiting, visited, result);
			if ("TABLE".equals(scope.kind()) && scope.detail() != null) {
				var tableScope = new Scope(scope.owner(), scope.kind(), scope.object(), null, scope.privilege());
				for (SqlOperation parent : parents.getOrDefault(new GrantKey(tableScope, grant.grantor()), List.of()))
					append(parent, parents, visiting, visited, result);
			}
		}
		visiting.remove(operation);
		visited.add(operation);
		result.add(operation);
	}
}
