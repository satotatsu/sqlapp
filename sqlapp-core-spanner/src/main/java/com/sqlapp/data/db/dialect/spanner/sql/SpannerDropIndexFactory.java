/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

/** GoogleSQL DROP for regular, vector and search indexes. */
public class SpannerDropIndexFactory extends AbstractDropNamedObjectFactory<Index, SpannerSqlBuilder> {
	@Override
	protected void addDropObject(final Index index, final SpannerSqlBuilder builder) {
		SpannerIndexValidation.requireUserIndex(index);
		SpannerIndexValidation.identity(index);
		if (index.getTable() != null && index.getTable().getConstraints().contains(index.getName())) {
			throw new UnsupportedOperationException(
					"Cloud Spanner constraint-owned index requires a constraint migration: " + index.getName());
		}
		builder.drop();
		if (index.getIndexType() == IndexType.FullText) {
			builder.space()._add("SEARCH");
		}
		// GoogleSQL permits DROP INDEX for vector indexes too.
		builder.index().ifExists(getOptions().isDropIfExists()).name(index, getOptions().isDecorateSchemaName());
	}
}
