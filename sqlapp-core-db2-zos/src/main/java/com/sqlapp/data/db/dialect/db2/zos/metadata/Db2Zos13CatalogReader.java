/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.common.metadata.AbstractDb2HostCatalogReader;

/**
 * Owns Db2 for z/OS catalog SQL; row mapping is shared across the host
 * dialects.
 */
public class Db2Zos13CatalogReader extends AbstractDb2HostCatalogReader {
	public Db2Zos13CatalogReader(Dialect dialect) {
		super(dialect, Db2Zos13CatalogReader.class);
	}
}
