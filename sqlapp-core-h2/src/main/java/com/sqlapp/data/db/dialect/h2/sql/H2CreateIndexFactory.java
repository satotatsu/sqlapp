/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.ReferenceColumn;

/** Preserve native index null ordering alongside shared key generation. */
public class H2CreateIndexFactory extends AbstractCreateIndexFactory<H2SqlBuilder> {
	@Override
	protected void addColumn(ReferenceColumn column, H2SqlBuilder builder) {
		super.addColumn(column, builder);
		if (column.getNullsOrder() != null)
			builder.space()._add(column.getNullsOrder());
	}
}
