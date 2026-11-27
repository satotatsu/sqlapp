/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.common.metadata.AbstractDb2HostCatalogReader;

/**
 * Owns Db2 for i catalog SQL; row mapping is shared across the host dialects.
 */
public class Db2I75CatalogReader extends AbstractDb2HostCatalogReader {
	public Db2I75CatalogReader(Dialect dialect) {
		super(dialect, Db2I75CatalogReader.class);
	}
}
