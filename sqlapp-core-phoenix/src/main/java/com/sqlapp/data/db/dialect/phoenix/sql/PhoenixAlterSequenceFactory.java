/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.State;

/** Prevents sequence settings from being silently ignored during migration. */
public class PhoenixAlterSequenceFactory extends SimpleSqlFactory<Sequence, PhoenixSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Sequence sequence) {
		throw new UnsupportedOperationException(
				"Phoenix sequence changes require a separate migration: " + sequence.getName());
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		return difference.getState() == State.Unchanged ? List.of() : super.createDiffSql(difference);
	}
}
