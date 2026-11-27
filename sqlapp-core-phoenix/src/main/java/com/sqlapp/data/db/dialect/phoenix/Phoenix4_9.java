/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 4.9 introduces column DEFAULT declarations. */
public class Phoenix4_9 extends Phoenix {
	private static final long serialVersionUID = 1L;

	protected Phoenix4_9(final Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsColumnDefault() {
		return true;
	}
}
