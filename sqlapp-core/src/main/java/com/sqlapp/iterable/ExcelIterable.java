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

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;

/**
 * ExcelIterable
 * 
 * @author tatsuo satoh
 * 
 */
public class ExcelIterable extends AbstractMapIterable {
	private final DataFormat workbookFormat;

	public ExcelIterable(File file) {
		super(file);
		this.workbookFormat = null;
	}

	public ExcelIterable(File file, DataFormat format) {
		super(file);
		this.workbookFormat = requireWorkbookFormat(format);
	}

	public ExcelIterable(Path path) {
		super(path);
		this.workbookFormat = null;
	}

	public ExcelIterable(Path path, DataFormat format) {
		super(path);
		this.workbookFormat = requireWorkbookFormat(format);
	}

	public ExcelIterable(InputStream inputStream) {
		this(inputStream, DataFormat.EXCEL);
	}

	public ExcelIterable(InputStream inputStream, DataFormat format) {
		super(inputStream);
		this.workbookFormat = requireWorkbookFormat(format);
	}

	public ExcelIterable(Reader reader) {
		super(reader);
		this.workbookFormat = null;
	}

	private static DataFormat requireWorkbookFormat(final DataFormat format) {
		if (format == null || !format.isWorkbook()) {
			throw new IllegalArgumentException("Workbook format is required: " + format);
		}
		return format;
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(File file) {
		return new ExcelIterator(file, workbookFormat);
	}

	protected <T> Iterator<Map<String, Object>> iteratorInternal(T obj, XmlReaderOptions options,
			ExceptionConsumer<Table> cons) {
		final Table table = new Table();
		final VirtualThreadIterable<Map<String, Object>> itr = new VirtualThreadIterable<>(output -> {
			options.setAddRow((tbl, row) -> {
				output.accept(row.toMapSimple());
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
		return new ExcelIterator(path, workbookFormat);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(InputStream inputStream) {
		return new ExcelIterator(inputStream, workbookFormat);
	}

	@Override
	protected Iterator<Map<String, Object>> iterator(Reader reader) {
		throw new UnsupportedOperationException("iterator(Reader reader)");
	}

	public static class ExcelIterator implements Iterator<Map<String, Object>>, AutoCloseable {

		ExcelIterator(final File file) {
			this(file, null);
		}

		ExcelIterator(final File file, final DataFormat format) {
			this.file = file;
			this.inputStream = null;
			this.workbookFormat = format;
		}

		ExcelIterator(final InputStream inputStream) {
			this(inputStream, DataFormat.EXCEL);
		}

		ExcelIterator(final InputStream inputStream, final DataFormat format) {
			this.file = null;
			this.inputStream = inputStream;
			this.workbookFormat = format;
		}

		ExcelIterator(final Path path) {
			this(path.toFile());
		}

		ExcelIterator(final Path path, final DataFormat format) {
			this(path.toFile(), format);
		}

		private final File file;
		private final InputStream inputStream;
		private final DataFormat workbookFormat;
		private Workbook workbook;
		private boolean initialized = false;
		private boolean closed;

		private final Map<Number, Column> columnIndexColumnMap = CommonUtils.map();

		private final Map<Number, Boolean> columnIndexFixedTypeMap = CommonUtils.map();

		private Iterator<org.apache.poi.ss.usermodel.Row> rowIterator;

		private void initialize() {
			if (initialized) {
				return;
			}
			preInitialize();
			initializeColumn();
			initialized = true;
		}

		private void preInitialize() {
			try {
				if (file != null) {
					final DataFormat format = workbookFormat != null ? workbookFormat : DataFormat.parse(file);
					this.workbook = format.createWorkBook(file, null, true);
				} else {
					this.workbook = workbookFormat.createWorkBook(inputStream);
				}
			} catch (EncryptedDocumentException | InvalidFormatException | IOException e) {
				throw new RuntimeException(e);
			}
		}

		protected org.apache.poi.ss.usermodel.Row read() {
			return rowIterator.next();
		}

		protected boolean hasNextInternal() {
			return rowIterator.hasNext();
		}

		protected void initializeColumn() {
			final Sheet sheet = workbook.getSheetAt(0);
			rowIterator = sheet.rowIterator();
			org.apache.poi.ss.usermodel.Row headerRow;
			if (hasNextInternal()) {
				headerRow = read();
			} else {
				return;
			}
			headerRow.forEach(cell -> {
				final Object obj = ExcelUtils.getCellValue(cell);
				if (!(obj instanceof String)) {
					return;
				}
				final String columnName = (String) obj;
				final Column column = new Column(columnName);
				columnIndexColumnMap.put(cell.getColumnIndex(), column);
				columnIndexFixedTypeMap.put(cell.getColumnIndex(), false);
			});
		}

		protected void set(final org.apache.poi.ss.usermodel.Row excelRow, final Map<String, Object> map) {
			excelRow.forEach(cell -> {
				final Column column = columnIndexColumnMap.get(cell.getColumnIndex());
				if (column == null) {
					return;
				}
				final Object value = ExcelUtils.getCellValue(cell);
				if (value != null) {
					map.put(column.getName(), value);
				}
			});
		}

		@Override
		public void close() throws Exception {
			if (closed) {
				return;
			}
			closed = true;
			Exception failure = null;
			if (workbook != null) {
				try {
					workbook.close();
				} catch (final Exception e) {
					failure = e;
				}
			}
			if (inputStream != null) {
				try {
					inputStream.close();
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

		@Override
		public boolean hasNext() {
			initialize();
			return hasNextInternal();
		}

		@Override
		public Map<String, Object> next() {
			initialize();
			org.apache.poi.ss.usermodel.Row row = read();
			Map<String, Object> map = CommonUtils.linkedMap();
			set(row, map);
			return map;
		}
	}
}
