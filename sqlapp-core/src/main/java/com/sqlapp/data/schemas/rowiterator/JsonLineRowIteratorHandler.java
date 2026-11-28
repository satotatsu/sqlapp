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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.JsonConverter;

/**
 * JSON line Iterator
 * 
 * @author tatsuo satoh
 *
 */
public class JsonLineRowIteratorHandler extends AbstractRowIteratorHandler {

	private final File file;
	private final JsonConverter jsonConverter;

	public JsonLineRowIteratorHandler(final File file, final JsonConverter jsonConverter,
			final RowValueConverter valueConverter) {
		super(valueConverter);
		this.file = file;
		this.jsonConverter = jsonConverter;
	}

	public JsonLineRowIteratorHandler(final File file, final JsonConverter jsonConverter) {
		super((r, c, v) -> v);
		this.file = file;
		this.jsonConverter = jsonConverter;
	}

	public JsonLineRowIteratorHandler(final File file) {
		super((r, c, v) -> v);
		this.file = file;
		this.jsonConverter = new JsonConverter();
	}

	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		return new JsonlineRowIterator(c, file, jsonConverter, 0L, this.getRowValueConverter());
	}

	public static class JsonlineRowIterator extends AbstractRowIterator<Map<String, Object>> {
		JsonlineRowIterator(final RowCollection c, final File file, final JsonConverter jsonConverter, final long index,
				final RowValueConverter valueConverter) {
			super(c, index, valueConverter);
			this.rowSchema = new JsonRowSchema(table);
			this.file = file;
			this.jsonConverter = jsonConverter;
		}

		JsonlineRowIterator(final RowCollection c, final Reader reader, final JsonConverter jsonConverter,
				final long index, final RowValueConverter valueConverter) {
			super(c, index, valueConverter);
			this.rowSchema = new JsonRowSchema(table);
			this.file = null;
			this.jsonConverter = jsonConverter;
			if (reader instanceof BufferedReader) {
				this.reader = (BufferedReader) reader;
			} else {
				this.reader = new BufferedReader(reader);
			}
		}

		private final JsonConverter jsonConverter;

		private final File file;
		private final String charset = "utf8";
		private BufferedReader reader;
		private String filename;
		private Map<String, Object> current = null;
		private long lineNumber;
		private final JsonRowSchema rowSchema;

		@Override
		protected void preInitialize() throws Exception {
			if (file != null) {
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				this.filename = file.getAbsolutePath();
			} else {
				this.filename = null;
			}
		}

		@Override
		protected void initializeColumn() throws Exception {
		}

		@Override
		protected boolean hasNextInternal() throws Exception {
			readInternal();
			return current != null;
		}

		private boolean readed = false;

		@SuppressWarnings("unchecked")
		protected Map<String, Object> readInternal() throws Exception {
			if (readed) {
				return current;
			}
			final String value = reader.readLine();
			current = null;
			if (value != null) {
				lineNumber++;
				if (value.isBlank()) {
					throw invalidRow();
				}
				current = JsonRowSchema.reader(jsonConverter, Map.class).readValue(value);
				if (current == null) {
					throw invalidRow();
				}
			}
			readed = true;
			return current;
		}

		private IllegalArgumentException invalidRow() {
			return new IllegalArgumentException("Expected a JSON object at line " + lineNumber
					+ (filename == null ? "" : " in " + filename));
		}

		@Override
		protected Map<String, Object> read() throws Exception {
			current = readInternal();
			readed = false;
			return current;
		}

		@Override
		protected void set(final Map<String, Object> map, final Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceRowNumber(count + 1);
			map.forEach((columnName, value) -> {
				final Column column = rowSchema.resolve(columnName, name -> searchColumn(table, name));
				if (column != null) {
					if (value != null) {
						put(row, column, rowSchema.prepare(column, value));
					}
				}
			});
		}

		@Override
		protected void doClose() {
			final Reader currentReader = this.reader;
			this.reader = null;
			if (currentReader == null) {
				return;
			}
			try {
				currentReader.close();
			} catch (final RuntimeException e) {
				throw e;
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

}
