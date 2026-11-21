/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 4.8 introduces OFFSET. */
public class Phoenix4_8 extends Phoenix4_7 {
	private static final long serialVersionUID = 1L;

	protected Phoenix4_8(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsLimitOffset() {
		return true;
	}

	@Override
	public boolean supportsAppendOnlySchema() {
		return true;
	}
}
