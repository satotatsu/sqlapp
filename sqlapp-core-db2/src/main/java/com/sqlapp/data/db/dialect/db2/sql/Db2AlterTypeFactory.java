/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class Db2AlterTypeFactory extends Db2CreateTypeFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() != State.Added)
			throw new IllegalArgumentException(
					"Db2 type changes or removals require an explicit native dependency plan");
		return createSql(difference.getTarget(Type.class));
	}
}
