/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.virtica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;

/** Schema reader for Vertica 11.1.1 and later. */
public class Virtica11_1_1SchemaReader extends VirticaSchemaReader {
	@Override
	protected com.sqlapp.data.db.metadata.ProjectionReader newProjectionReader() {
		return new VirticaProjectionReader(getDialect());
	}

	protected Virtica11_1_1SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new Virtica11_1_1ProcedureReader(this.getDialect());
	}
}
