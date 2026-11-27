/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;

/**
 * Preserve native index kind (HASH/SPATIAL), UNIQUE null treatment and null
 * ordering alongside shared key generation. Index types H2 lacks are created as
 * ordinary indexes.
 */
public class H2CreateIndexFactory extends AbstractCreateIndexFactory<H2SqlBuilder> {
	@Override
	protected void addUnique(Index index, Table table, H2SqlBuilder builder) {
		String policy = index.getSpecifics().get(H2NullsDistinct.KEY);
		if (policy != null && !index.isUnique()) {
			throw new IllegalArgumentException(
					H2NullsDistinct.KEY + " applies only to UNIQUE indexes: " + index.getName());
		}
		IndexType type = index.getIndexType();
		if (index.isUnique()) {
			if (type == IndexType.Spatial) {
				throw new IllegalArgumentException("H2 SPATIAL indexes cannot be UNIQUE: " + index.getName());
			}
			builder.unique();
			String clause = H2NullsDistinct.clause(getDialect(), policy);
			if (clause != null) {
				builder.space()._add(clause);
			}
		} else if (type == IndexType.Spatial) {
			builder.space()._add("SPATIAL");
		}
		if (type == IndexType.Hash) {
			builder.space()._add("HASH");
		}
		builder.index().ifNotExists(table != null && getOptions().isCreateIfNotExists()).space();
	}

	@Override
	protected void addColumn(ReferenceColumn column, H2SqlBuilder builder) {
		super.addColumn(column, builder);
		if (column.getNullsOrder() != null)
			builder.space()._add(column.getNullsOrder());
	}
}
