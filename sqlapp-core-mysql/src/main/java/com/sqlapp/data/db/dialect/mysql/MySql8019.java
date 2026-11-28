package com.sqlapp.data.db.dialect.mysql;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/** MySQL 8.0.19 adds row aliases for INSERT ... ON DUPLICATE KEY UPDATE. */
public class MySql8019 extends MySql801 {
	private static final long serialVersionUID = 1L;

	protected MySql8019(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}
