/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;

/**
 * Explicit, synchronous Phoenix index rebuild; never inferred from a state
 * diff.
 */
public class PhoenixRebuildIndexFactory extends SimpleSqlFactory<Index, PhoenixSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Index index) {
		if (index.getTable() == null || index.getName() == null || index.getName().isBlank()
				|| index.getTable().getName() == null || index.getTable().getName().isBlank())
			throw new IllegalArgumentException("Phoenix REBUILD INDEX requires an index name and parent table/view");
		final var builder = createSqlBuilder();
		builder.alter().index().name(index, false).on().name(index.getTable(), getOptions().isDecorateSchemaName())
				.space()._add("REBUILD");
		final List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.REBUILD, index);
		return result;
	}
}
