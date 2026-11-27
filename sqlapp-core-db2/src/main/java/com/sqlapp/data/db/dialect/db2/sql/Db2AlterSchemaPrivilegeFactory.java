/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaPrivilege;
import com.sqlapp.data.schemas.State;

/** Grant-option reductions need an explicit revoke/regrant decision. */
public class Db2AlterSchemaPrivilegeFactory extends SimpleSqlFactory<SchemaPrivilege, Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		SchemaPrivilege original = (SchemaPrivilege) difference.getOriginal();
		SchemaPrivilege target = (SchemaPrivilege) difference.getTarget();
		if (original != null && target != null && original.isGrantable() && !target.isGrantable()) {
			throw new IllegalArgumentException(
					"Reducing Db2 schema GRANT OPTION requires an explicit REVOKE and regrant plan; REVOKE affects grants from all grantors");
		}
		if (original != null && target != null
				&& !java.util.Objects.equals(original.getGrantorName(), target.getGrantorName())) {
			throw new IllegalArgumentException(
					"Db2 schema grantor changes require executing with the intended grantor authority");
		}
		if (original != null && target != null && !java.util.Objects.equals(original.getSpecifics().get("GRANTEE_TYPE"),
				target.getSpecifics().get("GRANTEE_TYPE"))) {
			throw new IllegalArgumentException(
					"Changing Db2 schema grantee type requires an explicit REVOKE and GRANT for the two principal identities");
		}
		SchemaPrivilege object = target == null ? original : target;
		SqlType type = target == null ? SqlType.REVOKE : SqlType.GRANT;
		SqlFactory<SchemaPrivilege> factory = getSqlFactoryRegistry().getSqlFactory(object, type);
		return new ArrayList<>(factory.createSql(object));
	}

	@Override
	public List<SqlOperation> createSql(SchemaPrivilege privilege) {
		SqlFactory<SchemaPrivilege> factory = getSqlFactoryRegistry().getSqlFactory(privilege, SqlType.GRANT);
		return factory.createSql(privilege);
	}
}
