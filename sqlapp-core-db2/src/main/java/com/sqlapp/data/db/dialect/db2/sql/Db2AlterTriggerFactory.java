/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

public class Db2AlterTriggerFactory extends Db2CreateTriggerFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Modified)
			throw new IllegalArgumentException(
					"Db2 trigger changes require an explicit replacement plan preserving trigger activation order");
		return super.createDiffSql(difference);
	}
}
