/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

public class Db2AlterSequenceFactory extends Db2_1110CreateSequenceFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		if (difference.getState() == State.Modified)
			throw new IllegalArgumentException(
					"Db2 sequence changes require an explicit ALTER plan preserving live allocation state");
		return super.createDiffSql(difference);
	}
}
