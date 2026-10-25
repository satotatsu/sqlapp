/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.db2.util.Db2CreationContext;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Trigger;

/** Native trigger DDL owns REFERENCING, WHEN and compound SQL semantics. */
public class Db2CreateTriggerFactory extends SimpleSqlFactory<Trigger, Db2SqlBuilder> {
	@Override
	protected List<Trigger> sort(List<Trigger> triggers) {
		// Activation order follows creation order. For manually authored snapshots
		// without timestamps, preserve the caller's explicit collection order.
		if (triggers.stream().anyMatch(trigger -> trigger.getCreatedAt() == null))
			return triggers;
		var sorted = new ArrayList<>(triggers);
		sorted.sort(java.util.Comparator.comparing(Trigger::getCreatedAt));
		return sorted;
	}

	@Override
	public List<SqlOperation> createSql(Trigger trigger) {
		if (trigger.getDefinition().isEmpty())
			throw new IllegalArgumentException(
					"Db2 trigger requires complete CREATE TRIGGER definition: " + trigger.getName());
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : Db2CreationContext.sql(trigger.getSpecifics().get("QUALIFIER"),
				trigger.getSpecifics().get("FUNC_PATH")))
			addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, trigger);
		for (String sql : Db2DefinitionSql.statements(trigger.getDefinition(), getDialect()))
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, trigger);
		if (trigger.getRemarks() != null && !trigger.getRemarks().isEmpty())
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON TRIGGER " + name(trigger, getOptions().isDecorateSchemaName()) + " IS ")
							.sqlChar(trigger.getRemarks()),
					SqlType.SET_COMMENT, trigger);
		return result;
	}

	static String name(Trigger trigger, boolean decorate) {
		return decorate && trigger.getSchemaName() != null
				? Db2CreationContext.qualified(trigger.getSchemaName(), trigger.getName())
				: Db2CreationContext.identifier(trigger.getName());
	}
}
