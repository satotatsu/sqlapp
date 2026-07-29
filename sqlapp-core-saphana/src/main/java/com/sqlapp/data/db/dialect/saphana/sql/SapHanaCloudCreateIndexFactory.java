/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/**
 * SAP HANA Cloud CREATE INDEX.
 */
public class SapHanaCloudCreateIndexFactory
		extends SapHanaCreateIndexFactory {

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SapHanaSqlBuilder builder) {
		if (index.getIndexType() == IndexType.FullText) {
			throw new IllegalArgumentException(
					"SAP HANA Cloud does not support FULLTEXT indexes; "
							+ "use a fuzzy search index: "
							+ index.getName());
		}
		super.addObjectDetail(index, table, builder);
	}
}
