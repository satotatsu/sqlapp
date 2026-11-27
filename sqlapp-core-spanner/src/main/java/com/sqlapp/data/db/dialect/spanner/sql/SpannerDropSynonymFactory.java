/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Synonym;

/** GoogleSQL removes a table alias with ALTER TABLE DROP SYNONYM. */
public class SpannerDropSynonymFactory extends AbstractDropNamedObjectFactory<Synonym, SpannerSqlBuilder> {
	@Override
	protected void addDropObject(final Synonym synonym, final SpannerSqlBuilder builder) {
		SpannerCreateSynonymFactory.addSynonymAction(synonym, builder, false, getOptions().isDecorateSchemaName());
	}
}
