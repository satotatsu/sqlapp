/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.schemas.rowiterator;

import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.util.CommonUtils;

/**
 * Combined RowIteratorHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class CombinedRowIteratorHandler implements RowIteratorHandler {

	private List<RowIteratorHandler> rowIteratorHandlers = null;

	public CombinedRowIteratorHandler(List<RowIteratorHandler> rowIteratorHandlers) {
		this.rowIteratorHandlers = rowIteratorHandlers;
	}

	public CombinedRowIteratorHandler(RowIteratorHandler... rowIteratorHandlers) {
		this.rowIteratorHandlers = CommonUtils.list(rowIteratorHandlers);
	}

	private List<Iterator<Row>> getAbstractRowIterator(RowCollection c) {
		return rowIteratorHandlers.stream().map(a -> a.iterator(c)).collect(Collectors.toList());
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		return new CombinedRowIterator(c, getAbstractRowIterator(c));
	}

	public static class CombinedRowIterator implements Iterator<Row>, AutoCloseable {

		private final List<Iterator<Row>> rowListIterators;
		private int handlerIndex = 0;
		private Iterator<Row> current;
		private boolean closed;
		private final Set<Iterator<Row>> closedIterators = java.util.Collections
				.newSetFromMap(new IdentityHashMap<>());

		protected CombinedRowIterator(RowCollection c, List<Iterator<Row>> rowListIterators) {
			this.rowListIterators = rowListIterators;
		}

		protected Iterator<Row> getRowListIterator() {
			if (closed) {
				return null;
			}
			if (current != null) {
				return current;
			}
			try {
				while (handlerIndex < rowListIterators.size()) {
					final Iterator<Row> iterator = rowListIterators.get(handlerIndex);
					if (iterator.hasNext()) {
						return iterator;
					}
					closeOne(iterator);
					handlerIndex++;
				}
				closed = true;
				return null;
			} catch (final RuntimeException | Error e) {
				closeOnFailure(e);
				throw e;
			} catch (final Exception e) {
				closeOnFailure(e);
				throw new RuntimeException(e);
			}
		}

		@Override
		public boolean hasNext() {
			current = getRowListIterator();
			return current != null;
		}

		@Override
		public Row next() {
			Row row = nextInternal();
			return row;
		}

		protected Row nextInternal() {
			final Iterator<Row> iterator = current != null ? current : getRowListIterator();
			current = null;
			if (iterator == null) {
				throw new NoSuchElementException();
			}
			try {
				return iterator.next();
			} catch (final RuntimeException | Error e) {
				closeOnFailure(e);
				throw e;
			}
		}

		@Override
		public void close() throws Exception {
			if (closed) {
				return;
			}
			closed = true;
			current = null;
			Exception failure = null;
			for (final Iterator<Row> iterator : rowListIterators) {
				try {
					closeOne(iterator);
				} catch (final Exception e) {
					if (failure == null) {
						failure = e;
					} else if (failure != e) {
						failure.addSuppressed(e);
					}
				}
			}
			if (failure != null) {
				throw failure;
			}
		}

		private void closeOnFailure(final Throwable failure) {
			try {
				close();
			} catch (final Exception closeFailure) {
				if (failure != closeFailure) {
					failure.addSuppressed(closeFailure);
				}
			}
		}

		private void closeOne(final Iterator<Row> iterator) throws Exception {
			if (closedIterators.add(iterator) && iterator instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}
}
