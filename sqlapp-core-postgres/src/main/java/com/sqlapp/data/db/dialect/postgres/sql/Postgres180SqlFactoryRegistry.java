/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.UniqueConstraint;

public class Postgres180SqlFactoryRegistry extends Postgres170SqlFactoryRegistry {
	public Postgres180SqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE,
				Postgres180CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT, Postgres180InsertFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE, Postgres180UpdateFactory.class);
		registerSqlFactory(Table.class, SqlType.DELETE, Postgres180DeleteFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE, Postgres180MergeFactory.class);
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				Postgres180CreateUniqueConstraintFactory.class);
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				Postgres180CreateForeignKeyConstraintFactory.class);
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE,
				Postgres180CreateCheckConstraintFactory.class);
		registerSqlFactory(NotNullConstraint.class, SqlType.CREATE,
				Postgres180CreateNotNullConstraintFactory.class);
	}
}
