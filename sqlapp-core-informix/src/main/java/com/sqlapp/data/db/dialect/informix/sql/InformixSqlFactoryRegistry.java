/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SimpleSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.UniqueConstraint;

/** SQL factory registry for Informix-specific syntax. */
public class InformixSqlFactoryRegistry extends SimpleSqlFactoryRegistry {
	public InformixSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				InformixCreateUniqueConstraintFactory.class);
	}
}
