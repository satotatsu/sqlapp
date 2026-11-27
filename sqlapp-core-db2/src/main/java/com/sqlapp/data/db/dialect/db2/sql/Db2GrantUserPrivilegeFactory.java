/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.db2.Db2DatabaseAuthority;
import com.sqlapp.data.db.dialect.db2.Db2_950;
import com.sqlapp.data.db.dialect.db2.Db2_970;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.PrivilegeState;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.UserPrivilege;

/** Database authorities, including explicit separated DBADM semantics. */
public class Db2GrantUserPrivilegeFactory extends SimpleSqlFactory<UserPrivilege, Db2SqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		UserPrivilege object = UserPrivilege.class
				.cast(difference.getState() == State.Deleted ? difference.getOriginal() : difference.getTarget());
		if (difference.getState() == State.Deleted) {
			if (object.getState() != null && object.getState() != PrivilegeState.Grant)
				throw new IllegalArgumentException("Db2 database difference must remove a held grant");
			return createSql(object.clone().setState(PrivilegeState.Revoke));
		}
		return createSql(object);
	}

	@Override
	public List<SqlOperation> createSql(UserPrivilege privilege) {
		Db2DatabaseAuthority action;
		try {
			action = Db2DatabaseAuthority.valueOf(privilege.getPrivilege() == null ? ""
					: privilege.getPrivilege().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unsupported Db2 database authority: " + privilege.getPrivilege(), e);
		}
		if (!action.supports(getDialect()))
			throw new IllegalArgumentException(
					"Db2 database authority is not supported by this dialect version: " + action);
		if (privilege.isAdmin() || privilege.isGrantable())
			throw new IllegalArgumentException("Db2 database authorities do not support ADMIN or GRANT OPTION");
		if (privilege.getState() != null
				&& privilege.getState() != (isGrant() ? PrivilegeState.Grant : PrivilegeState.Revoke))
			throw new IllegalArgumentException("Privilege state conflicts with requested Db2 GRANT/REVOKE");
		String type = privilege.getSpecifics().get("GRANTEE_TYPE");
		if (action == Db2DatabaseAuthority.SECADM && !(getDialect() instanceof Db2_970) && !"U".equals(type))
			throw new IllegalArgumentException("Db2 before 9.7 supports SECADM only for USER principals");
		if ("R".equals(type) && !(getDialect() instanceof Db2_950))
			throw new IllegalArgumentException("Db2 database grants to ROLE require Db2 LUW 9.5 or later");
		if ("PUBLIC".equals(privilege.getGranteeName())
				&& Set.of(Db2DatabaseAuthority.DBADM, Db2DatabaseAuthority.SECADM, Db2DatabaseAuthority.DATAACCESS,
						Db2DatabaseAuthority.ACCESSCTRL, Db2DatabaseAuthority.CREATE_SECURE_OBJECT).contains(action))
			throw new IllegalArgumentException(
					"Db2 database authority cannot be granted to or revoked from PUBLIC: " + action);
		String model = privilege.getSpecifics().get("DBADM_MODEL");
		if (model != null && (!Set.of("LEGACY", "SEPARATE").contains(model) || action != Db2DatabaseAuthority.DBADM))
			throw new IllegalArgumentException("DBADM_MODEL must be LEGACY or SEPARATE and is only valid for DBADM");
		boolean separate = getDialect() instanceof Db2_970;
		if (isGrant() && model != null && separate != "SEPARATE".equals(model))
			throw new IllegalArgumentException(
					"Db2 DBADM across the 9.7 boundary requires an explicit DATAACCESS/ACCESSCTRL decision; set DBADM_MODEL for the intended target and model additional grants separately");
		var builder = createSqlBuilder();
		builder._add(isGrant() ? "GRANT " : "REVOKE ")._add(action.name());
		if (isGrant() && action == Db2DatabaseAuthority.DBADM && separate)
			builder._add(" WITHOUT DATAACCESS WITHOUT ACCESSCTRL");
		builder._add(" ON DATABASE ")._add(isGrant() ? "TO " : "FROM ");
		Db2PrivilegeSql.grantee(builder, privilege.getGranteeName(), type);
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, privilege);
		return result;
	}
}
