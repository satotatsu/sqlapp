/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.EventReader;

/** Schema reader for Vertica 12.0.4 and later. */
public class Virtica12_0_4SchemaReader extends Virtica11_1_1SchemaReader {

	protected Virtica12_0_4SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected EventReader newEventReader() {
		return new Virtica12_0_4EventReader(this.getDialect());
	}
}
