/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.ObjectPrivilege;
import com.sqlapp.data.schemas.PrivilegeState;

/** Native object privileges, including non-EXECUTE routine grants. */
public class SapHanaGrantObjectPrivilegeFactory extends SimpleSqlFactory<ObjectPrivilege, SapHanaSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(final ObjectPrivilege privilege) {
		if (isGrant() && "FALSE".equalsIgnoreCase(privilege.getSpecifics().get("IS_VALID")))
			throw new IllegalArgumentException(
					"Cannot replay an invalid SAP HANA object grant; resolve its authorization dependencies explicitly");
		String action = privilege.getPrivilege() == null ? ""
				: privilege.getPrivilege().toUpperCase(java.util.Locale.ROOT);
		boolean schemaPrivilege = "SCHEMA".equalsIgnoreCase(privilege.getSpecifics().get("OBJECT_TYPE"));
		if (!Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "REFERENCES", "TRIGGER", "ALTER", "DROP", "INDEX",
				"EXECUTE", "DEBUG", "DEBUG MODIFY", "SELECT METADATA", "UNMASKED", "ALL PRIVILEGES").contains(action)
				&& !(schemaPrivilege && Set.of("CREATE ANY", "CREATE TEMPORARY TABLE",
						"CREATE OBJECT STRUCTURED PRIVILEGE", "CREATE VIRTUAL PACKAGE").contains(action)))
			throw new IllegalArgumentException("Unsupported SAP HANA object privilege: " + privilege.getPrivilege());
		String objectName = privilege.getObjectName();
		if (schemaPrivilege && (objectName == null || objectName.isBlank()))
			objectName = privilege.getSchemaName();
		if (objectName == null || objectName.isBlank() || privilege.getGranteeName() == null
				|| privilege.getGranteeName().isBlank())
			throw new IllegalArgumentException("SAP HANA object privilege requires an object and grantee");
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Object privilege state conflicts with requested GRANT/REVOKE");
		var builder = createSqlBuilder()._add(isGrant() ? "GRANT " : "REVOKE ")._add(action)._add(" ON ");
		if (schemaPrivilege)
			builder._add("SCHEMA ");
		else if (getOptions().isDecorateSchemaName() && privilege.getSchemaName() != null)
			builder.name(privilege.getSchemaName())._add(".");
		builder.name(objectName)._add(isGrant() ? " TO " : " FROM ").name(privilege.getGranteeName());
		if (isGrant() && privilege.isGrantable())
			builder._add(" WITH GRANT OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
