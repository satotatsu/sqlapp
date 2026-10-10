/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach;

import com.sqlapp.data.db.dialect.postgres.Postgres110;
import com.sqlapp.data.db.dialect.postgres.sql.Postgres110SqlFactoryRegistry;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.db.dialect.cockroach.metadata.CockroachCatalogReader;
import com.sqlapp.data.db.dialect.cockroach.sql.*;

/** CockroachDB 24.3+; PostgreSQL wire compatibility does not imply catalog compatibility. */
public class CockroachDB extends Postgres110 {
	private static final long serialVersionUID = 1L;
	public CockroachDB() { super(() -> null); }
	@Override public String getProductName() { return "CockroachDB"; }
	@Override public String getSimpleName() { return "cockroach"; }
	@Override public CatalogReader getCatalogReader() { return new CockroachCatalogReader(this); }
	@Override public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new Postgres110SqlFactoryRegistry(this) {
			@Override protected void initializeAllStateSqls() {
				super.initializeAllStateSqls();
				for(var type:java.util.List.<Class<?>>of(Schema.class,Index.class,Function.class,Sequence.class,Type.class,View.class)) registerSqlFactory(type,State.Modified,SqlType.ALTER);
			}
			@Override protected void initializeAllSqls() {
				super.initializeAllSqls();
				registerSqlFactory(Catalog.class, SqlType.CREATE, CockroachCreateCatalogFactory.class);
				registerSqlFactory(Schema.class, SqlType.CREATE, CockroachCreateSchemaFactory.class);
				registerSqlFactory(Schema.class, SqlType.ALTER, CockroachAlterSchemaFactory.class);
				registerSqlFactory(View.class, SqlType.ALTER, CockroachAlterViewFactory.class);
				for(var type:java.util.List.<Class<?>>of(UniqueConstraint.class,CheckConstraint.class,ForeignKeyConstraint.class)) registerSqlFactory(type,SqlType.DROP,CockroachDropConstraintFactory.class);

				registerSqlFactory(Table.class, SqlType.ALTER, CockroachAlterTableFactory.class);
				registerSqlFactory(Table.class, SqlType.DROP, CockroachDropTableFactory.class);
				registerSqlFactory(Index.class, SqlType.DROP, CockroachDropIndexFactory.class);
				registerSqlFactory(Index.class, SqlType.ALTER, CockroachAlterIndexFactory.class);
				registerSqlFactory(Function.class, SqlType.ALTER, CockroachAlterFunctionFactory.class);
				registerSqlFactory(Sequence.class, SqlType.ALTER, CockroachAlterSequenceFactory.class);
				registerSqlFactory(Type.class, SqlType.ALTER, CockroachAlterTypeFactory.class);

				registerSqlFactory(Index.class, SqlType.CREATE, CockroachCreateIndexFactory.class);
				registerSqlFactory(Table.class, SqlType.CREATE, CockroachCreateTableFactory.class);
				registerSqlFactory(Sequence.class, SqlType.CREATE, CockroachCreateSequenceFactory.class);
				registerSqlFactory(Table.class, SqlType.MERGE, CockroachMergeFactory.class);
			}
		};
	}
}
