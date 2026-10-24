/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.UserPrivilege;

/** Direct privilege differences share the Catalog authority planner. */
public class Db2AlterUserPrivilegeFactory extends SimpleSqlFactory<UserPrivilege, Db2SqlBuilder> {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Catalog before = new Catalog(), after = new Catalog();
		if (difference.getOriginal() != null)
			before.getUserPrivileges().add(((UserPrivilege) difference.getOriginal()).clone());
		if (difference.getTarget() != null)
			after.getUserPrivileges().add(((UserPrivilege) difference.getTarget()).clone());
		var plan = Db2DatabasePrivilegePlan.diff(before, after, getSqlFactoryRegistry());
		List<SqlOperation> result = new ArrayList<>(plan.before());
		result.addAll(plan.after());
		return result;
	}

	@Override
	public List<SqlOperation> createSql(UserPrivilege privilege) {
		SqlFactory<UserPrivilege> factory = getSqlFactoryRegistry().getSqlFactory(privilege, SqlType.GRANT);
		return factory.createSql(privilege);
	}
}
