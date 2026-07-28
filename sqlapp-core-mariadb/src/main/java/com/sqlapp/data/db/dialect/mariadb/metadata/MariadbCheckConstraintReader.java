/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mysql.metadata.MySqlCheckConstraintReader;

/**
 * MariaDB check-constraint metadata reader.
 */
public class MariadbCheckConstraintReader extends MySqlCheckConstraintReader {

	public MariadbCheckConstraintReader(Dialect dialect) {
		super(dialect);
	}
}
