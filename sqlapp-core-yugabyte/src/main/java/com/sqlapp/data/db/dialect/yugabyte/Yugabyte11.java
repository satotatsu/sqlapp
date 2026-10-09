/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte;

import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres110CatalogReader;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres110SchemaReader;
import com.sqlapp.data.db.dialect.yugabyte.metadata.YugabyteSequenceReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;

import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres110SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.yugabyte.sql.YugabyteMergeFactory;

/** YSQL compatibility baseline for PostgreSQL 11. Vendor extensions are not modeled. */
public class Yugabyte11 extends Postgres110 {
	private static final long serialVersionUID = 1L;

	public Yugabyte11() {
		super(() -> null);
	}

	@Override
	public String getProductName() {
		return "YugabyteDB";
	}

	@Override
	public String getSimpleName() {
		return "yugabyte";
	}
	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres110SqlFactoryRegistry(this) {
			@Override
			protected void initializeAllSqls() {
				super.initializeAllSqls();
				registerSqlFactory(com.sqlapp.data.schemas.Function.class, SqlType.ALTER,
						com.sqlapp.data.db.dialect.yugabyte.sql.Yugabyte11AlterFunctionFactory.class);
				registerSqlFactory(Table.class, SqlType.MERGE,
						YugabyteMergeFactory.class);
			}
		};
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Postgres110CatalogReader(this) {
			@Override
			protected SchemaReader newSchemaReader() {
				return new Postgres110SchemaReader(getDialect()) {
					@Override
					protected SequenceReader newSequenceReader() {
						return new YugabyteSequenceReader(getDialect());
					}
				};
			}
		};
	}

}
