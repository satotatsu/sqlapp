/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SequenceReader;

/**
 * MariaDB 11.5 schema reader.
 */
public class MariadbSchema11_50Reader extends MariadbSchema10_27Reader {

	public MariadbSchema11_50Reader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return new MariadbSequence11_50Reader(getDialect());
	}
}
