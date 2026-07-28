/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.TableReader;

class MariadbSchema11_40Reader extends MariadbSchema10_27Reader {

	MariadbSchema11_40Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new MariadbTable11_40Reader(getDialect());
	}
}
