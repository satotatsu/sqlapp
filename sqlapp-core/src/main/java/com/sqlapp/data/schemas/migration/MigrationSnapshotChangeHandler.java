/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas.migration;

/** Checked-exception-capable sink used by the streaming snapshot planner. */
@FunctionalInterface
public interface MigrationSnapshotChangeHandler<E extends Exception> {
	void accept(MigrationSnapshotChange change) throws E;
}
