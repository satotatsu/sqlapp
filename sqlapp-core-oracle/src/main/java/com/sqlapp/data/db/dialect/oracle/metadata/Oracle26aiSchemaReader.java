/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ViewReader;
import com.sqlapp.data.db.metadata.DomainReader;

public class Oracle26aiSchemaReader extends Oracle23aiSchemaReader {
	protected Oracle26aiSchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ViewReader newViewReader() {
		return new Oracle26aiViewReader(getDialect());
	}

	@Override
	protected DomainReader newDomainReader() {
		return new Oracle26aiDomainReader(getDialect());
	}
}
