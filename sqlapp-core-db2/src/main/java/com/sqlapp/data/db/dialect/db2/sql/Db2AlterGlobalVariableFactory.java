/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class Db2AlterGlobalVariableFactory extends Db2CreateGlobalVariableFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() == State.Modified)
			throw new IllegalArgumentException(
					"Db2 global variable changes require an explicit native recreation plan; active session values and dependencies must be coordinated");
		Constant variable = difference.getState() == State.Added ? difference.getTarget(Constant.class)
				: difference.getOriginal(Constant.class);
		SqlFactory<Constant> factory = getSqlFactoryRegistry().getSqlFactory(variable,
				difference.getState() == State.Added ? SqlType.CREATE : SqlType.DROP);
		return factory.createSql(variable);
	}
}
