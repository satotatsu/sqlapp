/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;

/** Creates a schema synonym from its resolved target identity. */
public class SapHanaCreateSynonymFactory extends AbstractCreateSynonymFactory<SapHanaSqlBuilder> {
	@Override
	protected void addCreateObject(final Synonym synonym, final SapHanaSqlBuilder builder) {
		final String definition = toString(synonym.getDefinition());
		if (definition != null && !definition.isBlank()) {
			builder._add(definition);
			return;
		}
		if (synonym.getObjectName() == null || synonym.getObjectName().isBlank()) {
			throw new IllegalArgumentException("SAP HANA synonym requires a target object name: " + synonym.getName());
		}
		if (synonym.getDbLinkName() != null && !synonym.getDbLinkName().isBlank()
				&& (synonym.getObjectSchemaName() == null || synonym.getObjectSchemaName().isBlank())) {
			throw new IllegalArgumentException(
					"SAP HANA remote synonym requires a target schema: " + synonym.getName());
		}
		builder.create().space()._add("SYNONYM").name(synonym, getOptions().isDecorateSchemaName()).space()._add("FOR")
				.space();
		if (synonym.getDbLinkName() != null && !synonym.getDbLinkName().isBlank())
			builder.name(synonym.getDbLinkName())._add(".");
		if (synonym.getObjectSchemaName() != null) {
			builder.name(synonym.getObjectSchemaName())._add(".");
		}
		builder.name(synonym.getObjectName());
	}
}
