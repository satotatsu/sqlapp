/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Db2 LUW 9.1: database security administrator authority. */
public class Db2_910 extends Db2 {
	private static final long serialVersionUID = 1L;

	public Db2_910(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}
