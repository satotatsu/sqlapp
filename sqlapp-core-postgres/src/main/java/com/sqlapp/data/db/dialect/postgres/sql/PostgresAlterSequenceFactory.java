/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.StandardAlterSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * PostgreSQL ALTER SEQUENCE: CACHE has no NO form and comments use COMMENT ON.
 */
public class PostgresAlterSequenceFactory extends StandardAlterSequenceFactory<PostgresSqlBuilder> {
	@Override
	protected void addCache(final Sequence sequence, final PostgresSqlBuilder builder) {
		final Integer size = sequence.getCacheSize();
		builder.space()._add("CACHE ")
				._add(!sequence.isCache() || size == null || size < 1 ? Integer.valueOf(1) : size);
	}

	@Override
	protected void addRemarks(final Sequence sequence, final List<SqlOperation> result) {
		final PostgresSqlBuilder builder = createSqlBuilder(getDialect());
		builder.comment().on().sequence().space().name(sequence, getOptions().isDecorateSchemaName()).is();
		if (sequence.getRemarks() == null) {
			builder.space()._add("NULL");
		} else {
			builder.sqlChar(sequence.getRemarks());
		}
		addSql(result, builder, SqlType.SET_COMMENT, sequence);
	}
}
