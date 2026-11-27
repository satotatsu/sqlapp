/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-virtica.
 */
package com.sqlapp.data.db.dialect.vertica.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;

/** Schema reader for Vertica 11.1.1 and later. */
public class Vertica11_1_1SchemaReader extends VerticaSchemaReader {
	@Override
	protected com.sqlapp.data.db.metadata.ProjectionReader newProjectionReader() {
		return new VerticaProjectionReader(getDialect());
	}

	protected Vertica11_1_1SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new Vertica11_1_1ProcedureReader(this.getDialect());
	}
}
