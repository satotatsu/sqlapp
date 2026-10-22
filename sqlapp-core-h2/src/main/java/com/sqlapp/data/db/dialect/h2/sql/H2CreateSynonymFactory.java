/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;

/** Native local synonym recreation from canonical target identity. */
public class H2CreateSynonymFactory extends AbstractCreateSynonymFactory<H2SqlBuilder> {
	@Override
	protected void addCreateObject(Synonym synonym, H2SqlBuilder builder) {
		if (!synonym.getDefinition().isEmpty()) {
			builder._add(synonym.getDefinition());
			return;
		}
		if (synonym.getObjectName() == null || synonym.getObjectName().isBlank()
				|| synonym.getObjectSchemaName() == null || synonym.getObjectSchemaName().isBlank())
			throw new IllegalArgumentException("H2 synonym requires target schema and object names");
		if (synonym.getDbLinkName() != null && !synonym.getDbLinkName().isBlank())
			throw new IllegalArgumentException("H2 synonyms do not support database links");
		builder.create().space()._add("SYNONYM").ifNotExists(getOptions().isCreateIfNotExists())
				.name(synonym, getOptions().isDecorateSchemaName()).for_().name(synonym.getObjectSchemaName())._add(".")
				.name(synonym.getObjectName());
	}
}
