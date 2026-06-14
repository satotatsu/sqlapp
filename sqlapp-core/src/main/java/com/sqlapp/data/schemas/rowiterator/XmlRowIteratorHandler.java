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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.iterable.VirtualThreadIterable;

/**
 * Combined RowIteratorHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class XmlRowIteratorHandler implements RowIteratorHandler {

	private File file = null;
	private RowValueConverter rowValueConverter = null;

	public XmlRowIteratorHandler(File file) {
		this.file = file;
		this.rowValueConverter = (r, c, v) -> v;
	}

	public XmlRowIteratorHandler(File file, RowValueConverter rowValueConverter) {
		this.file = file;
		this.rowValueConverter = rowValueConverter;
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		final Table table = c.getParent().clone();
		final XmlReaderOptions options = new XmlReaderOptions();
		options.setRowValueConverter(rowValueConverter);
		final VirtualThreadIterable<Row> itr = new VirtualThreadIterable<>(queue -> {
			options.setAddRow((tbl, row) -> {
				try {
					queue.put(row);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				return false;
			});
			try {
				table.loadXml(file, options);
			} catch (XMLStreamException | IOException e) {
				throw new RuntimeException(e);
			}
		});
		return itr.iterator();
	}
}
