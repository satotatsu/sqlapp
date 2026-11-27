/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.util.CommonUtils;

/** MySQL identifies an index within its owning table. */
public class MySqlDropIndexFactory extends AbstractDropNamedObjectFactory<Index, MySqlSqlBuilder> {

	@Override
	protected void addDropObject(Index index, MySqlSqlBuilder builder) {
		if (index.getTable() == null || CommonUtils.isEmpty(index.getTable().getName())) {
			throw new IllegalArgumentException("MySQL DROP INDEX requires its owning table: " + index.getName());
		}
		builder.drop().index().name(index, false).on();
		builder.name(index.getTable(), getOptions().isDecorateSchemaName());
	}
}
