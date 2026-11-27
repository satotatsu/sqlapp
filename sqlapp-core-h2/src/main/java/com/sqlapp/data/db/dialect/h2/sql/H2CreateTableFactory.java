/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.sql.CreateTableFactory;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * H2 CREATE TABLE.
 */
public class H2CreateTableFactory extends CreateTableFactory {

	@Override
	protected void addCreateObject(final Table table, final AbstractSqlBuilder<?> builder) {
		if (table.getTableType() == Table.TableType.Temporary)
			throw new IllegalArgumentException(
					"H2 temporary-table ON COMMIT policy is unavailable in the catalog; choose CREATE_TEMPORARY explicitly or execute native lifetime-policy DDL");
		builder.create();
		if (table.getTableType() == Table.TableType.Memory)
			builder.space()._add("MEMORY");
		else if (table.getTableType() == Table.TableType.Cache)
			builder.space()._add("CACHED");
		builder.table().ifNotExists(getOptions().isCreateIfNotExists()).space().name(table,
				getOptions().isDecorateSchemaName());
	}
}
