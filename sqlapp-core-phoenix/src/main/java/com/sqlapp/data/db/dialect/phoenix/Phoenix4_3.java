/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 4.3 introduces functional indexes. */
public class Phoenix4_3 extends Phoenix {
	private static final long serialVersionUID = 1L;

	protected Phoenix4_3(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsFunctionalIndexes() {
		return true;
	}

	@Override
	public boolean supportsStoreNulls() {
		return true;
	}
}
