/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-phoenix.
 */
package com.sqlapp.data.db.dialect.phoenix.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

/** Reserves a block of Phoenix sequence values in one statement. */
public class PhoenixSequenceNextValuesFactory extends SimpleSqlFactory<Sequence, PhoenixSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Sequence obj) {
		final PhoenixSqlBuilder builder = createSqlBuilder();
		builder.select().next().space()._add(getColumnParameterExpression(CONTEXT, "1"));
		builder.values().for_().name(obj);
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.SEQUENCE_NEXT_VALUES, obj);
		return sqlList;
	}
}
