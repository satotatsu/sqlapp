/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.schemas.Trigger;

/**
 * Informix triggers can contain several action phases and require complete
 * CREATE text.
 */
public class InformixCreateTriggerFactory extends AbstractCreateTriggerFactory<InformixSqlBuilder> {
	@Override
	protected void addCreateObject(final Trigger trigger, final InformixSqlBuilder builder) {
		if (trigger.getName() == null || trigger.getName().isBlank())
			throw new IllegalArgumentException("Informix trigger name is required to restore its mode");
		if (trigger.getDefinition() == null || trigger.getDefinition().isEmpty()
				|| trigger.getDefinition().stream().allMatch(String::isBlank))
			throw new IllegalArgumentException(
					"Complete Informix CREATE TRIGGER definition is required: " + trigger.getName());
		super.addCreateObject(trigger, builder);
	}

	@Override
	protected void addOtherDefinitions(final Trigger trigger,
			final java.util.List<com.sqlapp.data.db.sql.SqlOperation> result) {
		// Catalog CREATE text may retain an older mode after SET TRIGGERS changes it.
		final InformixSqlBuilder builder = createSqlBuilder();
		builder._add("SET TRIGGERS").space().name(trigger.getName().replace("\"", "\"\"")).space()
				._add(trigger.isEnable() ? "ENABLED" : "DISABLED");
		addSql(result, builder, com.sqlapp.data.db.sql.SqlType.ALTER, trigger);
	}
}
