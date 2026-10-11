/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util.iterator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/** Failure-preserving close support for iterators backed by external resources. */
public final class IteratorCloseUtils {
	private IteratorCloseUtils() {
	}

	/**
	 * Closes each distinct closeable iterator. When {@code failure} is present,
	 * close failures are attached to it. Otherwise the first close failure is
	 * thrown and later failures are suppressed on it.
	 */
	public static void close(final Throwable failure, final Iterator<?>... iterators) {
		RuntimeException closeFailure = null;
		final Set<Iterator<?>> closed = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final Iterator<?> iterator : iterators) {
			if (iterator == null || !closed.add(iterator) || !(iterator instanceof AutoCloseable closeable)) {
				continue;
			}
			try {
				closeable.close();
			} catch (final Exception e) {
				if (failure != null) {
					if (failure != e) {
						failure.addSuppressed(e);
					}
				} else if (closeFailure == null) {
					closeFailure = e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
				} else if (closeFailure != e) {
					closeFailure.addSuppressed(e);
				}
			}
		}
		if (failure == null && closeFailure != null) {
			throw closeFailure;
		}
	}
}
