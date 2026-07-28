/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.sql;

import com.sqlapp.data.db.dialect.mysql.util.MySqlSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

/**
 * MariaDB CREATE SEQUENCE generator.
 */
public class MariadbCreateSequenceFactory extends AbstractCreateSequenceFactory<MySqlSqlBuilder> {

	@Override
	protected void addIfNotExists(Sequence obj, MySqlSqlBuilder builder) {
		builder.ifNotExists();
	}
}
