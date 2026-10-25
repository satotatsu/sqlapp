/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;

public class Db2AlterDomainFactory extends Db2CreateDomainFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() != State.Added)
			throw new IllegalArgumentException(
					"Db2 type changes or removals require an explicit native plan to coordinate dependent columns and routines");
		return createSql(difference.getTarget(Domain.class));
	}
}
