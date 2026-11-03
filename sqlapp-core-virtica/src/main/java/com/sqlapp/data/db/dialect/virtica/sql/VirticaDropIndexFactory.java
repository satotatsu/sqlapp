/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

/**
 * Uses the text index's own identity, which may differ from its source schema.
 */
public class VirticaDropIndexFactory extends AbstractDropNamedObjectFactory<Index, VirticaSqlBuilder> {
	@Override
	protected void addDropObject(Index index, VirticaSqlBuilder builder) {
		if (index.getIndexType() != IndexType.FullText)
			throw new IllegalArgumentException("Vertica DROP requires a text index: " + index.getName());
		if (index.getName() == null || index.getName().isBlank())
			throw new IllegalArgumentException("Vertica text index DROP requires a name");
		builder.drop().space()._add("TEXT INDEX").ifExists(getOptions().isDropIfExists()).space();
		String schema = index.getSpecifics().get("INDEX_SCHEMA_NAME", String.class);
		if (schema == null)
			schema = index.getSchemaName();
		if (getOptions().isDecorateSchemaName() && schema != null)
			builder.name(schema)._add('.');
		builder.name(index.getName());
	}
}
