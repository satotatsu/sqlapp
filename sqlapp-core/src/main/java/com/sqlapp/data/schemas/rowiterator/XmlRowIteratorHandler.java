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
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.iterable.VirtualThreadIterable;
import com.sqlapp.util.FileUtils;

/**
 * Combined RowIteratorHandler
 * 
 * @author tatsuo satoh
 * 
 */
public class XmlRowIteratorHandler implements RowIteratorHandler {
	private final File file;
	private final Path path;
	private final InputStream inputStream;
	private final Reader reader;
	private RowValueConverter rowValueConverter = null;

	public XmlRowIteratorHandler(File file) {
		this(file, (r, c, v) -> v);
	}

	public XmlRowIteratorHandler(Path path) {
		this(path, (r, c, v) -> v);
	}

	public XmlRowIteratorHandler(InputStream inputStream) {
		this(inputStream, (r, c, v) -> v);
	}

	public XmlRowIteratorHandler(Reader reader) {
		this(reader, (r, c, v) -> v);
	}

	public XmlRowIteratorHandler(File file, RowValueConverter rowValueConverter) {
		this.file = file;
		this.path = null;
		this.inputStream = null;
		this.reader = null;
		this.rowValueConverter = rowValueConverter;
	}

	public XmlRowIteratorHandler(Path path, RowValueConverter rowValueConverter) {
		this.file = null;
		this.path = path;
		this.inputStream = null;
		this.reader = null;
		this.rowValueConverter = rowValueConverter;
	}

	public XmlRowIteratorHandler(InputStream inputStream, RowValueConverter rowValueConverter) {
		this.file = null;
		this.path = null;
		this.inputStream = inputStream;
		this.reader = null;
		this.rowValueConverter = rowValueConverter;
	}

	public XmlRowIteratorHandler(Reader reader, RowValueConverter rowValueConverter) {
		this.file = null;
		this.path = null;
		this.inputStream = null;
		this.reader = reader;
		this.rowValueConverter = rowValueConverter;
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		final Table table = c.getParent();
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
				loadXml(table, options);
			} catch (XMLStreamException | IOException e) {
				throw new RuntimeException(e);
			}
		}, () -> {
			closeResource();
		}, 20000);
		return itr.iterator();
	}

	private void loadXml(Table table, final XmlReaderOptions options) throws XMLStreamException, IOException {
		if (file != null) {
			table.loadXml(file, options);
			return;
		}
		if (path != null) {
			table.loadXml(path, options);
			return;
		}
		if (inputStream != null) {
			table.loadXml(inputStream, options);
			return;
		}
		table.loadXml(reader, options);
	}

	private void closeResource() {
		if (file != null) {
			return;
		}
		if (path != null) {
			return;
		}
		if (inputStream != null) {
			FileUtils.close(inputStream);
			return;
		}
		FileUtils.close(reader);
	}

}
