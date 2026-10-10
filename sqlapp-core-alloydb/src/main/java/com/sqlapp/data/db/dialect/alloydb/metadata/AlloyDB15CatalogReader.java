/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.*;
import com.sqlapp.data.db.metadata.*;

/** PostgreSQL 15 readers with AlloyDB index metadata. */
public class AlloyDB15CatalogReader extends Postgres150CatalogReader {
	public AlloyDB15CatalogReader(Dialect dialect) { super(dialect); }
	@Override
	protected SchemaReader newSchemaReader() {
		return new Postgres150SchemaReader(getDialect()) {
			@Override
			protected TableReader newTableReader() {
				return new Postgres130TableReader(getDialect()) {
					@Override
					protected IndexReader newIndexReader() { return new AlloyDBIndexReader(getDialect()); }
				};
			}
		};
	}
	@Override
	protected void setCommonAfter(java.sql.Connection connection, com.sqlapp.data.schemas.Catalog catalog) {
		super.setCommonAfter(connection, catalog);
		com.sqlapp.data.db.dialect.alloydb.AlloyDBColumnarConfiguration.read(catalog);
	}

}
