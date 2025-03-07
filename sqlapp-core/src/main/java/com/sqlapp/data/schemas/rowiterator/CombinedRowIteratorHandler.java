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
import java.util.ListIterator;
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

	private List<RowIteratorHandler> rowIteratorHandlers=null;
	
	public CombinedRowIteratorHandler(List<RowIteratorHandler> rowIteratorHandlers){
		this.rowIteratorHandlers=rowIteratorHandlers;
	}

	public CombinedRowIteratorHandler(RowIteratorHandler... rowIteratorHandlers){
		this.rowIteratorHandlers=CommonUtils.list(rowIteratorHandlers);
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		return new CombinedRowIterator(c,0, getAbstractRowListIterator(c));
	}

	@Override
	public ListIterator<Row> listIterator(RowCollection c, int index) {
		return new CombinedRowIterator(c,index, getAbstractRowListIterator(c));
	}

	@Override
	public ListIterator<Row> listIterator(RowCollection c) {
		return new CombinedRowIterator(c,0, getAbstractRowListIterator(c));
	}

	private List<ListIterator<Row>> getAbstractRowListIterator(RowCollection c){
		return rowIteratorHandlers.stream().map(a->a.listIterator(c)).collect(Collectors.toList());
	}
	

	public static class CombinedRowIterator implements ListIterator<Row>,AutoCloseable  {

		private List<ListIterator<Row>> rowListIterators;
		long index;
		int handlerIndex=0;
		
		protected CombinedRowIterator(RowCollection c, List<ListIterator<Row>> rowListIterators) {
			this.index=0;
			this.rowListIterators=rowListIterators;
		}

		protected CombinedRowIterator(RowCollection c, long index, List<ListIterator<Row>> rowListIterators) {
			this.index=index;
			this.rowListIterators=rowListIterators;
		}
		
		protected ListIterator<Row> getRowListIterator(){
			if (handlerIndex>=rowListIterators.size()){
				return null;
			}
			ListIterator<Row> itr= rowListIterators.get(handlerIndex);
			try {
				if (!itr.hasNext()){
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
			ListIterator<Row> itr=getRowListIterator();
			if (itr!=null){
				if (itr.hasNext()){
					return true;
				}
			}
			return false;
		}

		@Override
		public Row next() {
			Row row=nextInternal();
			return row;
		}

		protected Row nextInternal() {
			ListIterator<Row> itr=getRowListIterator();
			if (itr!=null){
				if (itr.hasNext()){
					return itr.next();
				}
			}
			return null;
		}
		
		@Override
		public void close() throws Exception {
			for(ListIterator<Row> itr:rowListIterators){
				if (itr instanceof AutoCloseable){
					((AutoCloseable)itr).close();
				}
			}
		}
		
		private void closeSilent(Object obj){
			if (obj instanceof AutoCloseable){
				try {
					((AutoCloseable)obj).close();
				} catch (Exception e) {
				}
			}
		}

		@Override
		public boolean hasPrevious() {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support hasPrevious.");
		}

		@Override
		public Row previous() {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support previous.");
		}

		@Override
		public int nextIndex() {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support nextIndex.");
		}

		@Override
		public int previousIndex() {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support previousIndex.");
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support remove.");
		}

		@Override
		public void set(Row e) {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support set.");
		}

		@Override
		public void add(Row e) {
			throw new UnsupportedOperationException(this.getClass()
					.getSimpleName() + " does not support add.");
		}
		
	}
}
