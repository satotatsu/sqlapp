/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** H2 2.2: explicit UNIQUE null treatment. */
public class H2_220 extends H2_200 {

	private static final long serialVersionUID = 1L;

	protected H2_220(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}
