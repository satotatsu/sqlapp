/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.Index;

public class Oracle23aiSqlFactoryRegistry extends Oracle21cSqlFactoryRegistry {

	public Oracle23aiSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Index.class, SqlType.CREATE, Oracle23aiCreateIndexFactory.class);
		registerSqlFactory(Domain.class, SqlType.CREATE, Oracle23aiCreateDomainFactory.class);
		registerSqlFactory(Domain.class, SqlType.DROP, Oracle23aiDropDomainFactory.class);
	}
}
