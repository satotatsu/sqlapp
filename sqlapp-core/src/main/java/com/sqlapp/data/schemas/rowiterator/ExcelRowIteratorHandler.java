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
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.CommonUtils;

/**
 * Excelの行のIterator
 * 
 * @author tatsuo satoh
 *
 */
public class ExcelRowIteratorHandler extends AbstractRowIteratorHandler {

	private final File file;
	private final DataFormat workbookFormat;
	private final int skipHeaderRowsSize;

	public ExcelRowIteratorHandler(final File file, final int skipHeaderRowsSize,
			final RowValueConverter valueConverter) {
		this(file, null, skipHeaderRowsSize, valueConverter);
	}

	public ExcelRowIteratorHandler(final File file, final DataFormat workbookFormat,
			final int skipHeaderRowsSize, final RowValueConverter valueConverter) {
		super(valueConverter);
		this.file = file;
		if (workbookFormat != null && !workbookFormat.isWorkbook()) {
			throw new IllegalArgumentException("Workbook format is required: " + workbookFormat);
		}
		this.workbookFormat = workbookFormat;
		this.skipHeaderRowsSize = skipHeaderRowsSize;
	}

	public ExcelRowIteratorHandler(final File file, final RowValueConverter valueConverter) {
		this(file, 1, valueConverter);
	}

	public ExcelRowIteratorHandler(final File file, final int skipHeaderRowsSize) {
		this(file, null, skipHeaderRowsSize, (r, c, v) -> v);
	}

	public ExcelRowIteratorHandler(final File file) {
		this(file, 1);
	}

	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		return new ExcelIterator(c, file, workbookFormat, 0L, this.getRowValueConverter(),
				this.skipHeaderRowsSize);
	}

	public static class ExcelIterator extends AbstractRowIterator<org.apache.poi.ss.usermodel.Row> {

		ExcelIterator(final RowCollection c, final File file, final long index, final RowValueConverter valueConverter,
				final int skipHeaderRowsSize) {
			this(c, file, null, index, valueConverter, skipHeaderRowsSize);
		}

		ExcelIterator(final RowCollection c, final File file, final DataFormat workbookFormat, final long index,
				final RowValueConverter valueConverter, final int skipHeaderRowsSize) {
			super(c, index, valueConverter);
			this.file = file;
			this.workbookFormat = workbookFormat;
			this.filename = file.getAbsolutePath();
			if (skipHeaderRowsSize < 0) {
				throw new IllegalArgumentException("skipHeaderRowsSize must not be negative");
			}
			this.skipHeaderRowsSize = skipHeaderRowsSize;
		}

		private final int skipHeaderRowsSize;
		private final File file;
		private final DataFormat workbookFormat;
		private Workbook workbook;
		private final String filename;

		private final Map<Number, Column> columnIndexColumnMap = CommonUtils.map();

		private final Map<Number, Boolean> columnIndexFixedTypeMap = CommonUtils.map();

		private Iterator<org.apache.poi.ss.usermodel.Row> rowIterator;

		@Override
		protected void preInitialize() throws Exception {
			if (skipHeaderRowsSize != 1 && CommonUtils.isEmpty(table.getColumns())) {
				throw new IllegalArgumentException(
						"Schema columns are required when Excel header rows are not 1: " + filename);
			}
			final DataFormat format = workbookFormat != null ? workbookFormat : DataFormat.parse(file);
			this.workbook = format.createWorkBook(file, null, true);
		}

		@Override
		protected org.apache.poi.ss.usermodel.Row read() {
			return rowIterator.next();
		}

		@Override
		protected boolean hasNextInternal() {
			return rowIterator.hasNext();
		}

		@Override
		protected void initializeColumn() throws IOException {
			final Sheet sheet = workbook.getSheetAt(0);
			rowIterator = sheet.rowIterator();
			if (skipHeaderRowsSize != 1) {
				for (int i = 0; i < skipHeaderRowsSize && rowIterator.hasNext(); i++) {
					rowIterator.next();
				}
				int position = 0;
				for (final Column column : table.getColumns()) {
					columnIndexColumnMap.put(position, column);
					columnIndexFixedTypeMap.put(position++, column.getDataType() != null);
				}
				return;
			}
			org.apache.poi.ss.usermodel.Row headerRow;
			if (hasNextInternal()) {
				headerRow = read();
			} else {
				return;
			}
			if (CommonUtils.isEmpty(table.getColumns())) {
				headerRow.forEach(cell -> {
					final Object obj = ExcelUtils.getCellValue(cell);
					if (!(obj instanceof String)) {
						return;
					}
					final String columnName = (String) obj;
					final Column column = new Column(columnName);
					columnIndexColumnMap.put(cell.getColumnIndex(), column);
					columnIndexFixedTypeMap.put(cell.getColumnIndex(), false);
					table.getColumns().add(column);
				});
			} else {
				final boolean[] hasType = new boolean[1];
				hasType[0] = true;
				headerRow.forEach(cell -> {
					final Object obj = ExcelUtils.getCellValue(cell);
					if (!(obj instanceof String)) {
						return;
					}
					final String columnName = (String) obj;
					final Column column = searchColumn(table, columnName);
					columnIndexFixedTypeMap.put(cell.getColumnIndex(), false);
					if (column != null) {
						if (column.getDataType() != null) {
							columnIndexFixedTypeMap.put(cell.getColumnIndex(), true);
						}
						columnIndexColumnMap.put(cell.getColumnIndex(), column);
					}
				});
			}
		}

		@Override
		protected void set(final org.apache.poi.ss.usermodel.Row excelRow, final Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceDetailInfo(excelRow.getSheet().getSheetName());
			row.setDataSourceRowNumber(excelRow.getRowNum() + 1);
			excelRow.forEach(cell -> {
				final Column column = columnIndexColumnMap.get(cell.getColumnIndex());
				if (column == null) {
					return;
				}
				final Boolean fixed = columnIndexFixedTypeMap.get(cell.getColumnIndex());
				final Object value = ExcelUtils.getCellValue(cell);
				if (value != null) {
					if (!fixed.booleanValue()) {
						ExcelUtils.setColumnType(cell, column);
						if (value instanceof String) {
							if (column.getLength() != null) {
								column.setLength(Math.max(this.getTypeLength((String) value), column.getLength()));
							} else {
								column.setLength(this.getTypeLength((String) value));
							}
						}
					}
					put(row, column, value);
				}
			});
		}

		@Override
		protected void doClose() {
			try {
				if (workbook != null) {
					workbook.close();
				}
			} catch (final IOException e) {
			}
		}
	}

}
