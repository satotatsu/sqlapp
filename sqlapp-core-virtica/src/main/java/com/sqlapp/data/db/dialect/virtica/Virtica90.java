/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Vertica 9.0.
 */
public class Virtica90 extends Virtica80 {

	private static final long serialVersionUID = 1L;

	protected Virtica90(final Supplier<Dialect> nextVersionDialectSupplier) {
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
