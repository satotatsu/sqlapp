/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte;

import com.sqlapp.data.db.dialect.postgres.Postgres150;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres150CatalogReader;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres150SchemaReader;
import com.sqlapp.data.db.dialect.yugabyte.metadata.YugabyteSequenceReader;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres150SqlFactoryRegistry;
import com.sqlapp.data.db.dialect.yugabyte.sql.YugabyteMergeFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/**
 * YSQL compatibility baseline for PostgreSQL 15. Placement metadata is
 * preserved through specifics.
 */
public class Yugabyte15 extends Postgres150 {
	private static final long serialVersionUID = 1L;

	public Yugabyte15() {
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
		return new Postgres150SqlFactoryRegistry(this) {
			@Override
			protected void initializeAllSqls() {
				super.initializeAllSqls();
				registerSqlFactory(Table.class, SqlType.CREATE,
						com.sqlapp.data.db.dialect.yugabyte.sql.YugabyteCreateTableFactory.class);
				registerSqlFactory(com.sqlapp.data.schemas.TableSpace.class, SqlType.CREATE,
						com.sqlapp.data.db.dialect.yugabyte.sql.YugabyteCreateTableSpaceFactory.class);
				registerSqlFactory(com.sqlapp.data.schemas.UniqueConstraint.class, SqlType.CREATE,
						com.sqlapp.data.db.dialect.yugabyte.sql.Yugabyte15CreateUniqueConstraintFactory.class);
				registerSqlFactory(com.sqlapp.data.schemas.Index.class, SqlType.CREATE,
						com.sqlapp.data.db.dialect.yugabyte.sql.Yugabyte15CreateIndexFactory.class);
				// YSQL PG15 does not implement SQL MERGE; retain INSERT ON CONFLICT.
				registerSqlFactory(Table.class, SqlType.MERGE, YugabyteMergeFactory.class);
			}
		};
	}

	@Override
	public CatalogReader getCatalogReader() {
		return new Postgres150CatalogReader(this) {
			@Override
			protected com.sqlapp.data.db.metadata.TableSpaceReader newTableSpaceReader() {
				return new com.sqlapp.data.db.dialect.yugabyte.metadata.YugabyteTableSpaceReader(getDialect());
			}

			@Override
			protected SchemaReader newSchemaReader() {
				return new Postgres150SchemaReader(getDialect()) {
					@Override
					protected com.sqlapp.data.db.metadata.TableReader newTableReader() {
						return new com.sqlapp.data.db.dialect.yugabyte.metadata.Yugabyte15TableReader(getDialect());
					}

					@Override
					protected SequenceReader newSequenceReader() {
						return new YugabyteSequenceReader(getDialect());
					}
				};
			}
		};
	}

}
