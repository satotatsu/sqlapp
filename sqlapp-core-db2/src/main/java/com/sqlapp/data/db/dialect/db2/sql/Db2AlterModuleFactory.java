/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Package;

public class Db2AlterModuleFactory extends Db2CreateModuleFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() != State.Added)
			throw new IllegalArgumentException(
					"Db2 module removal or modification requires an explicit native plan; DROP MODULE removes all members and invalidates dependents");
		return createSql(difference.getTarget(Package.class));
	}
}
