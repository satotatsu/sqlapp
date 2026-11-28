/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 4.7 introduces per-table metadata cache frequency. */
public class Phoenix4_7 extends Phoenix4_3 {
	private static final long serialVersionUID = 1L;

	protected Phoenix4_7(Supplier<Dialect> next) {
		super(next);
	}

	@Override
	public boolean supportsUpdateCacheFrequency() {
		return true;
	}

	@Override
	public boolean supportsTransactionalTables() {
		return true;
	}
}
