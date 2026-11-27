/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.View;

public class Oracle26aiSqlFactoryRegistry extends Oracle23aiSqlFactoryRegistry {
	public Oracle26aiSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(View.class, SqlType.CREATE, Oracle26aiCreateViewFactory.class);
	}
}
