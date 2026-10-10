/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.sql;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres150CreateIndexFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Order;

public class Yugabyte15CreateIndexFactory extends Postgres150CreateIndexFactory {
	@Override
	protected String catalogKeys(Index index) {
		// Multi-column HASH grouping is modeled by the YSQL placement reader.
		return YugabytePlacement.hashColumns(index.getSpecifics()) >= 0 ? null : super.catalogKeys(index);
	}

	@Override
	protected void addColumn(ReferenceColumn column, PostgresSqlBuilder builder) {
		Index index = column.getParent() == null ? null : column.getParent().getIndex();
		int hash = index == null ? -1 : YugabytePlacement.hashColumns(index.getSpecifics());
		if (hash < 0) {
			super.addColumn(column, builder);
			return;
		}
		if (hash > index.getColumns().size())
			throw new IllegalArgumentException("YSQL hash key exceeds index key columns");
		int position = index.getColumns().indexOf(column);
		if (position == 0 && hash > 1)
			builder._add("(");
		if (index.getIndexType() == com.sqlapp.data.schemas.IndexType.Function && column.getColumn() == null)
			builder.brackets(() -> builder._add(column.getName()));
		else
			builder.name(column);
		if (position < hash) {
			if (position == hash - 1)
				builder._add(hash > 1 ? ") HASH" : " HASH");
		} else {
			builder.space()._add(column.getOrder() == Order.Desc ? "DESC" : "ASC");
			if (column.getNullsOrder() != null)
				builder.space()._add(column.getNullsOrder());
		}
	}

	@Override
	protected void addFilter(Index index, Table table, PostgresSqlBuilder builder) {
		YugabytePlacement.split(index.getSpecifics(), builder);
		super.addFilter(index, table, builder);
	}
}
