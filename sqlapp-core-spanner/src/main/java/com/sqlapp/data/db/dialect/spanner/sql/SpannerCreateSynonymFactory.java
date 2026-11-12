/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSynonymFactory;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;

/** GoogleSQL table synonyms are added or removed through ALTER TABLE. */
public class SpannerCreateSynonymFactory extends AbstractCreateSynonymFactory<SpannerSqlBuilder> {
	@Override
	protected void addCreateObject(final Synonym synonym, final SpannerSqlBuilder builder) {
		addSynonymAction(synonym, builder, true, getOptions().isDecorateSchemaName());
	}

	static void addSynonymAction(final Synonym synonym, final SpannerSqlBuilder builder, final boolean add,
			final boolean decorateSchemaName) {
		final String alias = synonym.getName();
		final String table = synonym.getObjectName();
		if (alias == null || alias.isBlank() || table == null || table.isBlank() || alias.equalsIgnoreCase(table))
			throw new IllegalArgumentException("Spanner synonym requires distinct alias and table names");
		if (synonym.getDbLinkName() != null && !synonym.getDbLinkName().isEmpty())
			throw new UnsupportedOperationException("Spanner synonyms cannot reference a database link: " + alias);

		final String schema = synonym.getSchemaName() == null ? "" : synonym.getSchemaName();
		final String targetSchema = synonym.getObjectSchemaName() == null ? schema : synonym.getObjectSchemaName();
		if (!schema.equalsIgnoreCase(targetSchema))
			throw new UnsupportedOperationException("Spanner synonym and table must be in the same schema: " + alias);
		builder.alter().table().name(new Table(table).setSchemaName(targetSchema), decorateSchemaName).space()
				._add(add ? "ADD SYNONYM " : "DROP SYNONYM ").name(synonym, decorateSchemaName);
	}
}
