/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.i;

/**
 * Experimental Db2 for i 7.4 support. It shares the 7.5 catalog SQL; the only
 * modeled difference is the absence of the native BOOLEAN type introduced in
 * 7.5. Not verified against a server.
 */
public class Db2I74 extends Db2I75 {
	private static final long serialVersionUID = 1L;

	public Db2I74() {
		super();
	}

	@Override
	protected boolean supportsNativeBoolean() {
		return false;
	}
}
