/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2;

import java.util.function.Supplier;
import com.sqlapp.data.db.dialect.Dialect;

/** Db2 LUW 11.5.5: schema authorities and schema-wide data privileges. */
public class Db2_1155 extends Db2_1150 {

	private static final long serialVersionUID = 1L;

	public Db2_1155(Supplier<Dialect> nextVersionDialectSupplier) {
		super(nextVersionDialectSupplier);
	}
}
