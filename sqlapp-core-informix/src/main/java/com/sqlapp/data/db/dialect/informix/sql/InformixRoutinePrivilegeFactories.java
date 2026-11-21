/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import java.util.Set;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.dialect.informix.InformixRoutineSignature;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

/**
 * EXECUTE grants addressed by an Informix SPECIFIC name or an empty signature.
 */
public final class InformixRoutinePrivilegeFactories {
	private InformixRoutinePrivilegeFactories() {
	}

	private static String name(String value) {
		if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
			throw new IllegalArgumentException(
					"Informix routine privilege requires nonempty owner, specific name and grantee identifiers");
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private static String sql(RoutinePrivilege privilege, boolean grant) {
		if (!"EXECUTE".equalsIgnoreCase(privilege.getPrivilege()) || privilege.isHierachy()
				|| (privilege.getState() != null && privilege.getState() != PrivilegeState.Grant))
			throw new IllegalArgumentException("Informix routine privileges support ordinary EXECUTE grants only");
		final String kind = privilege.getSpecifics().get("INFORMIX_ROUTINE_KIND");
		if (!Set.of("FUNCTION", "PROCEDURE").contains(kind == null ? "" : kind))
			throw new IllegalArgumentException("INFORMIX_ROUTINE_KIND must be FUNCTION or PROCEDURE");
		String identity = privilege.getSpecifics().get("INFORMIX_ROUTINE_IDENTITY");
		String owner = name(privilege.getSchemaName());
		String routine;
		if ("SPECIFIC_NAME".equals(identity)) {
			routine = "SPECIFIC " + kind + " " + owner + "." + name(privilege.getSpecificName());
		} else if ("PARAMETER_TYPE_IDS".equals(identity)
				&& privilege.getSpecifics().get(InformixRoutineSignature.TYPES) != null) {
			routine = kind + " " + owner + "." + name(privilege.getObjectName()) + "("
					+ InformixRoutineSignature.sqlTypes(privilege.getSpecifics().get(InformixRoutineSignature.TYPES))
					+ ")";
		} else if ("PARAMETER_TYPE_IDS".equals(identity) && privilege.getObjectName() != null
				&& (privilege.getObjectName() + "()").equals(privilege.getSpecificName())) {
			routine = kind + " " + owner + "." + name(privilege.getObjectName()) + "()";
		} else {
			throw new IllegalArgumentException(
					"Informix routine grants require a SPECIFIC name or a resolved SQL signature");
		}
		String grantee = name(privilege.getGranteeName());
		if ("public".equalsIgnoreCase(privilege.getGranteeName()))
			grantee = "PUBLIC";
		if (privilege.isGrantable()
				&& ("PUBLIC".equals(grantee) || privilege.getGrantee() instanceof com.sqlapp.data.schemas.Role))
			throw new IllegalArgumentException("Informix WITH GRANT OPTION requires a user grantee");
		String result = (grant ? "GRANT" : "REVOKE") + " EXECUTE ON " + routine + (grant ? " TO " : " FROM ") + grantee;
		if (grant && privilege.isGrantable())
			result += " WITH GRANT OPTION";
		if (privilege.getGrantorName() != null)
			result += " AS " + name(privilege.getGrantorName());
		return result;
	}

	public static class Grant extends SimpleSqlFactory<RoutinePrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(RoutinePrivilege privilege) {
			return List.of(new SqlOperation(sql(privilege, true), SqlType.GRANT, privilege));
		}
	}

	public static class Revoke extends SimpleSqlFactory<RoutinePrivilege, InformixSqlBuilder> {
		@Override
		public List<SqlOperation> createSql(RoutinePrivilege privilege) {
			return List.of(new SqlOperation(sql(privilege, false), SqlType.REVOKE, privilege));
		}
	}

}
