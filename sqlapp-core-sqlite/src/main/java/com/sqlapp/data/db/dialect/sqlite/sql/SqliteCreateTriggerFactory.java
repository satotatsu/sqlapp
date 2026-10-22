/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.util.CommonUtils;

/** Recreates SQLite triggers from their complete executable definition. */
public class SqliteCreateTriggerFactory extends AbstractCreateTriggerFactory<SqliteSqlBuilder> {
	@Override
	protected void addCreateObject(Trigger trigger, SqliteSqlBuilder builder) {
		if (CommonUtils.isEmpty(trigger.getDefinition()))
			throw new IllegalArgumentException(
					"SQLite trigger requires complete CREATE TRIGGER definition: " + trigger.getName());
		String definition = toString(trigger.getDefinition());
		// TEMP triggers may target any attached database. Preserve a resolved
		// binding even when the destination connection has same-named tables.
		if ("temp".equalsIgnoreCase(trigger.getSchemaName()) && trigger.getTableSchemaName() != null)
			definition = SqliteDdl.qualifyTriggerTarget(definition, trigger.getTableSchemaName());
		builder._add(SqliteDdl.withIdentity(definition, "TRIGGER", trigger.getSchemaName(), trigger.getName()));
	}
}
