/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

/**
 * Experimental Db2 for i 7.6 support. It reuses the 7.5 catalog SQL and DDL
 * grammar; newer 7.6-only attributes are not modeled. Not verified against a
 * server.
 */
public class Db2I76 extends Db2I75 {
	private static final long serialVersionUID = 1L;

	public Db2I76() {
		super();
	}
}
