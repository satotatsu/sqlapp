/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.zos;

/**
 * Experimental Db2 12 for z/OS support. The catalog columns read by the Db2 13
 * dialect (including SYSVIEWS.STATEMENT, SYSKEYTARGETS, SYSCONTROLS and the
 * temporal/partitioning columns) also exist in Db2 12, so the 13 resources are
 * reused. Function-level dependent features remain subject to the subsystem's
 * activated function level. Not verified against a server.
 */
public class Db2Zos12 extends Db2Zos13 {
	private static final long serialVersionUID = 1L;

	public Db2Zos12() {
		super();
	}
}
