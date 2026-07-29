/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;

/**
 * Generates multiple values from a Vertica named sequence.
 */
public class VirticaSequenceNextValuesFactory
		extends SimpleSqlFactory<Sequence, VirticaSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Sequence obj) {
		final VirticaSqlBuilder builder = createSqlBuilder();
		builder._add("WITH RECURSIVE SQLAPP_SEQUENCE_ROWS(N) AS (");
		builder._add("SELECT 1 UNION ALL ");
		builder._add("SELECT N + 1 FROM SQLAPP_SEQUENCE_ROWS WHERE N < ");
		builder._add(getColumnParameterExpression(CONTEXT, "1"));
		builder._add(") SELECT NEXTVAL('");
		if (obj.getSchemaName() != null) {
			builder._add(obj.getSchemaName());
			builder._add(".");
		}
		builder._add(obj.getName());
		builder._add("') FROM SQLAPP_SEQUENCE_ROWS");
		final List<SqlOperation> sqlList = list();
		addSql(sqlList, builder, SqlType.SEQUENCE_NEXT_VALUES, obj);
		return sqlList;
	}
}
