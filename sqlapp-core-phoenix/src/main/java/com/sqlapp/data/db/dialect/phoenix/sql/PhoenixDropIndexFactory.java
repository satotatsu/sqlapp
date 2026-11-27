/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.Index;

public class PhoenixDropIndexFactory extends AbstractDropNamedObjectFactory<Index, PhoenixSqlBuilder> {
	@Override
	protected void addDropObject(Index index, PhoenixSqlBuilder builder) {
		if (index.getTable() == null || index.getName() == null || index.getName().isBlank())
			throw new IllegalArgumentException(
					"Phoenix DROP INDEX requires its name and parent table: " + index.getName());
		builder.drop().index().ifExists(getOptions().isDropIfExists()).name(index, false).on().name(index.getTable(),
				getOptions().isDecorateSchemaName());
	}
}
