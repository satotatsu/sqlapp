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

package com.sqlapp.iterable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;

/**
 * XmlRowIterable
 * 
 * @author tatsuo satoh
 * 
 */
public class XmlRowIterable extends AbstractTextMapIterable {

	public XmlRowIterable(File file) {
		super(file);
	}

	public XmlRowIterable(Path path) {
		super(path);
	}

	public XmlRowIterable(InputStream inputStream) {
		super(inputStream);
	}

	public XmlRowIterable(Reader reader) {
		super(reader);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(File file) {
		return iteratorInternal(file, table -> table.loadXml(file));
	}

	protected <T> Iterator<Map<String, Object>> iteratorInternal(T obj, ExceptionConsumer<Table> cons) {
		final Table table = new Table();
		final XmlReaderOptions options = new XmlReaderOptions();
		final VirtualThreadIterable<Map<String, Object>> itr = new VirtualThreadIterable<>(queue -> {
			options.setAddRow((tbl, row) -> {
				try {
					queue.put(row.toMapSimple());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				return false;
			});
			try {
				cons.accept(table);
			} catch (XMLStreamException | IOException e) {
				throw new RuntimeException(e);
			}
		});
		return itr.iterator();
	}

	@FunctionalInterface
	interface ExceptionConsumer<T> {
		void accept(T t) throws XMLStreamException, IOException;
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Path path) {
		return iteratorInternal(path, table -> table.loadXml(path));
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(InputStream inputStream) {
		return iteratorInternal(inputStream, table -> table.loadXml(inputStream));
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Reader reader) {
		return iteratorInternal(reader, table -> table.loadXml(reader));
	}
}
