/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.ProcedureReader;
import com.sqlapp.data.db.metadata.FunctionReader;

public class Firebird40SchemaReader extends Firebird30SchemaReader {
	protected Firebird40SchemaReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected ProcedureReader newProcedureReader() {
		return new Firebird40ProcedureReader(getDialect());
	}

	@Override
	protected FunctionReader newFunctionReader() {
		return new Firebird40FunctionReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.TriggerReader newTriggerReader() {
		return new Firebird40TriggerReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.TableReader newTableReader() {
		return new Firebird40TableReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.PackageReader newPackageReader() {
		return new Firebird40PackageReader(getDialect());
	}
}
