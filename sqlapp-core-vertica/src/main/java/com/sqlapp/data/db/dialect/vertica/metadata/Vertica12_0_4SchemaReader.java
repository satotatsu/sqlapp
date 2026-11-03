/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.EventReader;

/** Schema reader for Vertica 12.0.4 and later. */
public class Vertica12_0_4SchemaReader extends Vertica11_1_1SchemaReader {

	protected Vertica12_0_4SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected EventReader newEventReader() {
		return new Vertica12_0_4EventReader(this.getDialect());
	}
}
