/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Vertica 9.0.
 */
public class Vertica90 extends Vertica80 {

	private static final long serialVersionUID = 1L;

	protected Vertica90(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addUUID("UUID", type -> {
			type.setLiteral("'", "'");
		});
	}
}
