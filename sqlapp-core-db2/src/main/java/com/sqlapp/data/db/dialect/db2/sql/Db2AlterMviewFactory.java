/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

public class Db2AlterMviewFactory extends Db2CreateMviewFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Unchanged)
			return List.of();
		if (difference.getState() == State.Modified) {
			throw new IllegalArgumentException(
					"Db2 MQT changes require an explicit DROP/CREATE plan; data refresh and dependent objects must be coordinated");
		}
		return super.createDiffSql(difference);
	}
}
