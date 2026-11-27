/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.jdbc.metadata.JdbcSchemaReader;
import com.sqlapp.data.db.metadata.SequenceReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.metadata.ViewReader;

public class PhoenixSchemaReader extends JdbcSchemaReader {
	public PhoenixSchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableReader newTableReader() {
		return new PhoenixTableReader(getDialect());
	}

	@Override
	protected ViewReader newViewReader() {
		return new PhoenixViewReader(getDialect());
	}

	@Override
	protected SequenceReader newSequenceReader() {
		return new PhoenixSequenceReader(getDialect());
	}
}
