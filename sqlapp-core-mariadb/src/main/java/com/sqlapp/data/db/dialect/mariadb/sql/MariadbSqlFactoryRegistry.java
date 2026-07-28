/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlSqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.Table;

/**
 * MariaDB SQL factory registry.
 */
public class MariadbSqlFactoryRegistry extends MySqlSqlFactoryRegistry {

	public MariadbSqlFactoryRegistry(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void initializeAllSqls() {
		super.initializeAllSqls();
		registerSqlFactory(Table.class, SqlType.CREATE, MariadbCreateTableFactory.class);
		registerSqlFactory(Sequence.class, SqlType.CREATE, MariadbCreateSequenceFactory.class);
	}
}
