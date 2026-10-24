/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.RoutinePrivilege;

/**
 * HANA identifies a routine by schema/name rather than PostgreSQL signatures.
 */
public class SapHanaGrantRoutinePrivilegeFactory extends SimpleSqlFactory<RoutinePrivilege, SapHanaSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(final RoutinePrivilege privilege) {
		if (isGrant() && "FALSE".equalsIgnoreCase(privilege.getSpecifics().get("IS_VALID")))
			throw new IllegalArgumentException(
					"Cannot replay an invalid SAP HANA routine grant; resolve its authorization dependencies explicitly");
		if (!"EXECUTE".equalsIgnoreCase(privilege.getPrivilege())) {
			throw new IllegalArgumentException("SAP HANA routine privilege must be EXECUTE");
		}
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke)) {
			throw new IllegalArgumentException("Routine privilege state conflicts with requested GRANT/REVOKE");
		}
		if (privilege.getObjectName() == null || privilege.getObjectName().isBlank()
				|| privilege.getGranteeName() == null || privilege.getGranteeName().isBlank()) {
			throw new IllegalArgumentException("SAP HANA routine privilege requires an object name and grantee");
		}
		final var builder = createSqlBuilder()._add(isGrant() ? "GRANT EXECUTE ON " : "REVOKE EXECUTE ON ");
		if (getOptions().isDecorateSchemaName() && privilege.getSchemaName() != null) {
			builder.name(privilege.getSchemaName())._add(".");
		}
		builder.name(privilege.getObjectName())._add(isGrant() ? " TO " : " FROM ").name(privilege.getGranteeName());
		if (isGrant() && privilege.isGrantable())
			builder._add(" WITH GRANT OPTION");
		final List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
