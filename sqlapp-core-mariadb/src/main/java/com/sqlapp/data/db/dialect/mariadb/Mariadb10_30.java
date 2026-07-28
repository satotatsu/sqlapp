/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mariadb.
 */
package com.sqlapp.data.db.dialect.mariadb;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.mariadb.sql.MariadbSqlFactoryRegistry;
import com.sqlapp.data.db.dialect.mariadb.util.MariadbSqlBuilder;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;

/**
 * MariaDB 10.3 dialect.
 */
public class Mariadb10_30 extends Mariadb10_27 {

	private static final long serialVersionUID = 1L;

	protected Mariadb10_30(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsSequence() {
		return true;
	}

	@Override
	public String getSequenceNextValString(String sequenceName) {
		return "NEXT VALUE FOR " + sequenceName;
	}

	@Override
	public SqlFactoryRegistry createSqlFactoryRegistry() {
		return new MariadbSqlFactoryRegistry(this);
	}

	@Override
	public MariadbSqlBuilder createSqlBuilder() {
		return new MariadbSqlBuilder(this);
	}
}
