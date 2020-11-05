/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.schemas.rowiterator;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.function.RowValueConverter;

/**
 * Combined RowIteratorHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class XmlRowIteratorHandler implements RowIteratorHandler {

	private File file=null;
	private RowValueConverter rowValueConverter=null;

	public XmlRowIteratorHandler(File file){
		this.file=file;
		this.rowValueConverter=(r,c,v)->v;
	}

	public XmlRowIteratorHandler(File file, RowValueConverter rowValueConverter){
		this.file=file;
		this.rowValueConverter=rowValueConverter;
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		Table table=c.getParent().clone();
		BlockingDeque<Object> deque=new LinkedBlockingDeque<>(1024*128);
		Runnable r=loadXml(table, file, deque, 0);
		return new WrappedRowListIterator(r, table, deque);
	}

	private Runnable loadXml(final Table table, final File file, final BlockingDeque<Object> deque, final int index){
		XmlReaderOptions options=new XmlReaderOptions();
		options.setRowValueConverter(rowValueConverter);
		int[] count=new int[]{0};
		options.setAddRow((t,r)->{
			try {
				if (t==table){
					if (count[0]>=index){
						deque.put(r);
					} else{
						count[0]++;
					}
				} else{
					deque.put(DequeDummy.LAST);
				}
			} catch (InterruptedException e) {
				endDeque(deque);
			}
			return false;
		});
		Runnable r=()->{
			try {
				table.loadXml(file, options);
			} catch (XMLStreamException | IOException e) {
				throw new RuntimeException(e);
			} finally {
				endDeque(deque);
			}
		};
		return r;
	}
	
	private static enum DequeDummy{
		LAST
	}

	private void endDeque(final BlockingDeque<Object> deque){
		try {
			deque.put(DequeDummy.LAST);
		} catch (InterruptedException e) {
		}
	}
	
	@Override
	public ListIterator<Row> listIterator(RowCollection c, int index) {
		Table table=c.getParent().clone();
		BlockingDeque<Object> deque=new LinkedBlockingDeque<>(1024*128);
		Runnable r=loadXml(table, file, deque, index);
		return new WrappedRowListIterator(r, table, deque);
	}

	@Override
	public ListIterator<Row> listIterator(RowCollection c) {
		Table table=c.getParent().clone();
		BlockingDeque<Object> deque=new LinkedBlockingDeque<>(1024*128);
		Runnable r=loadXml(table, file, deque, 0);
		return new WrappedRowListIterator(r, table, deque);
	}
	
	
	static class WrappedRowListIterator extends AbstractListIterator{
		private final Thread thread;
		private boolean started=false;
		private final BlockingDeque<Object> deque;
	
		private Row current=null;
		
		protected WrappedRowListIterator(Runnable reader, Table table, BlockingDeque<Object> deque){
			this.thread=new Thread(reader);
			this.deque=deque;
		}

		@Override
		public void close() throws Exception {
			if (!started){
				thread.interrupt();
			}
		}

		@Override
		public boolean hasNext() {
			if (!started){
				thread.start();
				started=true;
			}
			if (this.current!=null){
				return true;
			}
			Object obj=null;
			try {
				obj=deque.take();
			} catch (InterruptedException e) {
				obj=null;
			}
			if (obj==DequeDummy.LAST){
				current=null;
			} else{
				current=(Row)obj;
			}
			return current!=null;
		}


		@Override
		public Row next() {
			Row ret=this.current;
			this.current=null;
			return ret;
		}
	}
}
