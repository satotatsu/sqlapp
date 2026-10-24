/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ObjectPrivilege;

public class FirebirdGrantObjectPrivilegeFactory extends SimpleSqlFactory<ObjectPrivilege, FirebirdSqlBuilder> {
	protected boolean isGrant() {
		return true;
	}

	@Override
	public List<SqlOperation> createSql(ObjectPrivilege object) {
		String type = object.getSpecifics().getOrDefault(FirebirdPrivilege.OBJECT_TYPE, "TABLE");

		var builder = createSqlBuilder();
		var specifics = new java.util.HashMap<>(object.getSpecifics());
		if (!specifics.containsKey(FirebirdPrivilege.GRANTEE_TYPE)
				&& object.getGrantee() instanceof com.sqlapp.data.schemas.Role)
			specifics.put(FirebirdPrivilege.GRANTEE_TYPE, "ROLE");
		FirebirdPrivilege.append(isGrant(), type, object.getPrivilege(), object.getObjectName(), null,
				object.getSchemaName(), object.getGranteeName(), object.getGrantorName(), object.isGrantable(),
				object.getState(), specifics, builder);
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, isGrant() ? SqlType.GRANT : SqlType.REVOKE, object);
		return result;
	}
}
