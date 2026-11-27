/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import com.sqlapp.data.db.dialect.duckdb.metadata.DuckDbIndexReader;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE INDEX for DuckDB. The index belongs to the schema of its table, so the
 * index name is never schema qualified. Expression keys read from DuckDB are
 * reproduced from {@link DuckDbIndexReader#INDEX_KEYS}.
 */
public class DuckDbCreateIndexFactory extends AbstractCreateIndexFactory<DuckDbSqlBuilder> {

	@Override
	public void addObjectDetail(final Index obj, final Table table, final DuckDbSqlBuilder builder) {
		if (!CommonUtils.isEmpty(obj.getWhere())) {
			throw new IllegalArgumentException("DuckDB does not support partial indexes: " + obj.getName());
		}
		addUnique(obj, table, builder);
		builder.name(obj, false);
		if (table != null) {
			builder.on();
			builder.name(table, getOptions().isDecorateSchemaName());
		}
		builder.space()._add("(");
		final String keys = obj.getSpecifics().get(DuckDbIndexReader.INDEX_KEYS);
		if (!CommonUtils.isEmpty(keys)) {
			builder.space()._add(keys);
		} else {
			int i = 0;
			for (final ReferenceColumn column : obj.getColumns()) {
				builder.comma(i++ > 0);
				builder.name(column);
				if (column.getOrder() == Order.Desc) {
					builder.space()._add(column.getOrder());
				}
			}
		}
		builder._add(")");
	}
}
