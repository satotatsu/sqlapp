/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Iterator;

/** Failure-preserving close support for migration row streams. */
final class BulkMigrationIteratorSupport {
	private BulkMigrationIteratorSupport() {
	}

	static void close(final Throwable failure, final Iterator<?>... iterators) {
		RuntimeException closeFailure = null;
		for (final Iterator<?> iterator : iterators) {
			if (!(iterator instanceof AutoCloseable closeable)) {
				continue;
			}
			try {
				closeable.close();
			} catch (Exception e) {
				if (failure != null) {
					failure.addSuppressed(e);
				} else if (closeFailure == null) {
					closeFailure = e instanceof RuntimeException runtime ? runtime
							: new IllegalStateException(e);
				} else {
					closeFailure.addSuppressed(e);
				}
			}
		}
		if (failure == null && closeFailure != null) {
			throw closeFailure;
		}
	}
}
