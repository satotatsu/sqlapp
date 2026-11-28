/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Apache Phoenix 5.3 introduces VARBINARY_ENCODED. */
public class Phoenix5_3 extends Phoenix4_9 {
	private static final long serialVersionUID = 1L;

	protected Phoenix5_3(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}

	@Override
	public boolean supportsEncodedBinary() {
		return true;
	}
}
