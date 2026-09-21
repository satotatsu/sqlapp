/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Iterator;

import com.sqlapp.util.iterator.IteratorCloseUtils;

/** Failure-preserving close support for migration row streams. */
final class BulkMigrationIteratorSupport {
	private BulkMigrationIteratorSupport() {
	}

	static void close(final Throwable failure, final Iterator<?>... iterators) {
		IteratorCloseUtils.close(failure, iterators);
	}
}
