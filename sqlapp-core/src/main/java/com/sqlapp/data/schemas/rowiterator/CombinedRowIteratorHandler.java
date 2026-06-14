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
import java.util.List;
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

		private List<Iterator<Row>> rowListIterators;
		int handlerIndex = 0;

		protected CombinedRowIterator(RowCollection c, List<Iterator<Row>> rowListIterators) {
			this.rowListIterators = rowListIterators;
		}

		protected Iterator<Row> getRowListIterator() {
			if (handlerIndex >= rowListIterators.size()) {
				return null;
			}
			Iterator<Row> itr = rowListIterators.get(handlerIndex);
			try {
				if (!itr.hasNext()) {
					closeSilent(itr);
					handlerIndex++;
					return getRowListIterator();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return itr;
		}

		@Override
		public boolean hasNext() {
			Iterator<Row> itr = getRowListIterator();
			if (itr != null) {
				if (itr.hasNext()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public Row next() {
			Row row = nextInternal();
			return row;
		}

		protected Row nextInternal() {
			Iterator<Row> itr = getRowListIterator();
			if (itr != null) {
				if (itr.hasNext()) {
					return itr.next();
				}
			}
			return null;
		}

		@Override
		public void close() throws Exception {
			for (Iterator<Row> itr : rowListIterators) {
				if (itr instanceof AutoCloseable) {
					((AutoCloseable) itr).close();
				}
			}
		}

		private void closeSilent(Object obj) {
			if (obj instanceof AutoCloseable) {
				try {
					((AutoCloseable) obj).close();
				} catch (Exception e) {
				}
			}
		}
	}
}
