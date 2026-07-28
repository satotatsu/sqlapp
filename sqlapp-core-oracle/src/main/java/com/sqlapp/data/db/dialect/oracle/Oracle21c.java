/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle;

import java.util.function.Supplier;

import com.sqlapp.data.db.dialect.Dialect;

/**
 * Oracle Database 21c dialect.
 */
public class Oracle21c extends Oracle19c {

	private static final long serialVersionUID = 1L;

	protected Oracle21c(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	protected void registerDataType() {
		super.registerDataType();
		getDbDataTypes().addJsonType(type -> {
			type.setCreateFormat("JSON");
		});
	}
}
