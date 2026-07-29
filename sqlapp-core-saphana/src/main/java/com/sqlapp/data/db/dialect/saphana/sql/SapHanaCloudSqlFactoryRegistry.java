/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-saphana.
 */
package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;

/**
 * SAP HANA Cloud SQL factory registry.
 */
public class SapHanaCloudSqlFactoryRegistry
		extends SapHanaSqlFactoryRegistry {

	public SapHanaCloudSqlFactoryRegistry(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Index.class, SqlType.CREATE,
				SapHanaCloudCreateIndexFactory.class);
	}
}
