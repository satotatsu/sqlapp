/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import com.sqlapp.data.db.dialect.informix.InformixRoutineSignature;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.RoutinePrivilegeReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.RoutinePrivilege;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * Reads all routine grants with their overload identities in a single query.
 */
public class InformixRoutinePrivilegeReader extends RoutinePrivilegeReader {
	private record Pending(RoutinePrivilege privilege, int routineId, int parameterCount) {
	}

	public InformixRoutinePrivilegeReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<RoutinePrivilege> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<RoutinePrivilege> result = new ArrayList<>();
		final List<Pending> pending = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("routinePrivileges.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String auth = getString(rs, "routine_auth");
				if (!"e".equals(auth) && !"E".equals(auth))
					throw new IllegalStateException("Unknown Informix routine privilege: " + auth);
				final var privilege = new RoutinePrivilege();
				privilege.setCatalogName(InformixSecurityMetadata.required(rs, CATALOG_NAME));
				privilege.setSchemaName(InformixSecurityMetadata.required(rs, SCHEMA_NAME));
				privilege.setObjectName(InformixSecurityMetadata.required(rs, ROUTINE_NAME));
				privilege.setSpecificName(InformixRoutineUtils.specificName(rs, privilege.getObjectName()));
				final String specific = getString(rs, "specific_name");
				privilege.getSpecifics().put("INFORMIX_ROUTINE_IDENTITY",
						specific != null && !specific.isBlank() ? "SPECIFIC_NAME" : "PARAMETER_TYPE_IDS");
				privilege.setPrivilege("EXECUTE");
				privilege.setHierachy(false);
				privilege.setGrantorName(InformixSecurityMetadata.required(rs, GRANTOR));
				privilege.setGranteeName(InformixSecurityMetadata.required(rs, GRANTEE));
				privilege.setGrantable("E".equals(auth));
				final String kind = InformixSecurityMetadata.required(rs, "routine_kind");
				if (!java.util.Set.of("FUNCTION", "PROCEDURE").contains(kind))
					throw new IllegalStateException("Unknown Informix routine kind: " + kind);
				privilege.getSpecifics().put("INFORMIX_ROUTINE_KIND", kind);
				int count = rs.getInt("argument_count");
				if ((specific == null || specific.isBlank()) && count > 0)
					pending.add(new Pending(privilege, rs.getInt("routine_id"), count));
				result.add(privilege);
			}
		});
		if (!pending.isEmpty())
			resolveSignatures(connection, pending);
		return result;
	}

	private void resolveSignatures(Connection connection, List<Pending> pending) {
		var parameters = new ParametersContext();
		parameters.put("routineIds", pending.stream().map(Pending::routineId).distinct().toList());
		Map<Integer, TreeMap<Integer, String>> signatures = new LinkedHashMap<>();
		execute(connection, getSqlNodeCache().getString("routinePrivilegeParameters.sql"), parameters,
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(ExResultSet rs) throws SQLException {
						int mode = rs.getInt("parameter_mode");
						if (mode == 3 || mode == 5)
							return;
						String type = InformixRoutineSignature.builtinType(rs.getInt("parameter_type"),
								rs.getInt("extended_type_id"), mode);
						signatures.computeIfAbsent(rs.getInt("routine_id"), ignored -> new TreeMap<>())
								.put(rs.getInt("parameter_id"), type);
					}
				});
		for (Pending grant : pending) {
			var types = signatures.get(grant.routineId());
			if (types != null && types.size() == grant.parameterCount()
					&& types.values().stream().noneMatch(java.util.Objects::isNull))
				grant.privilege().getSpecifics().put(InformixRoutineSignature.TYPES, String.join(",", types.values()));
		}
	}
}
