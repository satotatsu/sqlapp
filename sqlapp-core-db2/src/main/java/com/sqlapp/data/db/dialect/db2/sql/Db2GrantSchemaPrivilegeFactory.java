/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.sqlapp.data.db.dialect.db2.Db2_1155;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.State;

/** Native schema privileges; no expansion into individual object grants. */
public class Db2GrantSchemaPrivilegeFactory extends SimpleSqlFactory<SchemaPrivilege, Db2SqlBuilder> {

	private static final Set<String> LEGACY = Set.of("ALTERIN", "CREATEIN", "DROPIN");
	private static final Set<String> DATA = Set.of("SELECTIN", "INSERTIN", "UPDATEIN", "DELETEIN", "EXECUTEIN");
	private static final Set<String> AUTHORITIES = Set.of("SCHEMAADM", "ACCESSCTRL", "DATAACCESS", "LOAD");

	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		SchemaPrivilege object = SchemaPrivilege.class
				.cast(difference.getState() == State.Deleted ? difference.getOriginal() : difference.getTarget());
		// A removed, previously granted privilege is a REVOKE operation even when
		// the original explicitly recorded state=Grant. Preserve the source model.
		return createSql(object.clone().setState(isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke));
	}

	@Override
	public List<SqlOperation> createSql(SchemaPrivilege privilege) {
		String action = privilege.getPrivilege() == null ? "" : privilege.getPrivilege().toUpperCase(Locale.ROOT);
		if (!LEGACY.contains(action) && !DATA.contains(action) && !AUTHORITIES.contains(action))
			throw new IllegalArgumentException("Unsupported Db2 schema privilege: " + action);
		if (!LEGACY.contains(action) && !(getDialect() instanceof Db2_1155))
			throw new IllegalArgumentException("Db2 schema privilege " + action + " requires Db2 LUW 11.5.5 or later");
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with requested Db2 GRANT/REVOKE");
		if (privilege.getSchemaName() == null || privilege.getSchemaName().isBlank())
			throw new IllegalArgumentException("Db2 schema privilege requires a schema name");
		if (AUTHORITIES.contains(action)) {
			if (privilege.isGrantable())
				throw new IllegalArgumentException("Db2 schema authority cannot have WITH GRANT OPTION: " + action);
			if (isGrant() && "PUBLIC".equals(privilege.getGranteeName()))
				throw new IllegalArgumentException("Db2 schema authority cannot be granted to PUBLIC: " + action);
		}
		Db2SqlBuilder builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(action)._add(" ON SCHEMA ").name(privilege.getSchemaName())
				._add(isGrant() ? " TO " : " FROM ");
		Db2PrivilegeSql.grantee(builder, privilege.getGranteeName(), privilege.getSpecifics().get("GRANTEE_TYPE"));
		if (isGrant() && privilege.isGrantable())
			builder._add(" WITH GRANT OPTION");
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
