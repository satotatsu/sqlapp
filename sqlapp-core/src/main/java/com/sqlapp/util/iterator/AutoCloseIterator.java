/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util.iterator;

import java.io.Closeable;
import java.util.Iterator;

public class AutoCloseIterator<T> implements Iterator<T>, Closeable, AutoCloseable {

	private final Iterator<T> iterator;

	private boolean isClosed;

	public AutoCloseIterator(Iterator<T> iterator) {
		this.iterator = iterator;
		isClosed = false;
	}

	@Override
	public boolean hasNext() {
		if (isClosed) {
			return false;
		}
		try {
			final boolean hasNext = iterator.hasNext();
			if (!hasNext) {
				close();
			}
			return hasNext;
		} catch (final RuntimeException | Error e) {
			closeOnFailure(e);
			throw e;
		}
	}

	@Override
	public T next() {
		try {
			return iterator.next();
		} catch (final RuntimeException | Error e) {
			closeOnFailure(e);
			throw e;
		}
	}

	@Override
	public void close() {
		if (isClosed) {
			return;
		}
		isClosed = true;
		IteratorCloseUtils.close(null, iterator);
	}

	private void closeOnFailure(final Throwable failure) {
		if (isClosed) {
			return;
		}
		isClosed = true;
		IteratorCloseUtils.close(failure, iterator);
	}

}
