/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractSequenceNextValuesGenerateSeriesFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * Reserves sequence values with {@code SELECT nextval('seq') FROM
 * generate_series(1, n)}.
 */
public class DuckDbSequenceNextValuesFactory extends AbstractSequenceNextValuesGenerateSeriesFactory<DuckDbSqlBuilder> {

	@Override
	protected void addNextValueFor(final Sequence obj, final DuckDbSqlBuilder builder) {
		final String name = createSqlBuilder().name(obj, getOptions().isDecorateSchemaName()).toString();
		builder.space()._add("nextval(").sqlChar(name)._add(")");
	}
}
