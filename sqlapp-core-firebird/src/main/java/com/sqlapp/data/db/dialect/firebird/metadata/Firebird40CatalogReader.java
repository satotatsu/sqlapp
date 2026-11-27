/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;

public class Firebird40CatalogReader extends Firebird30CatalogReader {
	public Firebird40CatalogReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SchemaReader newSchemaReader() {
		return new Firebird40SchemaReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.RoleReader newRoleReader() {
		return new Firebird40RoleReader(getDialect());
	}

	@Override
	protected com.sqlapp.data.db.metadata.UserPrivilegeReader newUserPrivilegeReader() {
		return new Firebird40UserPrivilegeReader(getDialect());
	}
}
