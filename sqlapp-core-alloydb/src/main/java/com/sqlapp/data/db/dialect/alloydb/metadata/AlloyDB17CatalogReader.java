/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.metadata;

import com.sqlapp.data.db.dialect.Dialect;

/** PostgreSQL 17 retains the PostgreSQL 16 metadata catalog baseline. */
public class AlloyDB17CatalogReader extends AlloyDB16CatalogReader {
	public AlloyDB17CatalogReader(Dialect dialect) { super(dialect); }
}
