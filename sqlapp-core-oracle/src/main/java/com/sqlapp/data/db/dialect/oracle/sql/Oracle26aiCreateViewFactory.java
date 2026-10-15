/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

/** Oracle 26ai duality view replication options. */
public class Oracle26aiCreateViewFactory extends Oracle23aiCreateViewFactory {
	@Override
	protected boolean supportsLogicalReplication() {
		return true;
	}
}
