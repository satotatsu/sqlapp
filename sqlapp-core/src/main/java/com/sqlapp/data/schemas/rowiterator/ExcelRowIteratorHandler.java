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
import java.util.ListIterator;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Table;
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
	private final int skipHeaderRowsSize;

	public ExcelRowIteratorHandler(final File file, final int skipHeaderRowsSize,
			final RowValueConverter valueConverter) {
		super(valueConverter);
		this.file = file;
		this.skipHeaderRowsSize = skipHeaderRowsSize;
	}

	public ExcelRowIteratorHandler(final File file, final RowValueConverter valueConverter) {
		this(file, 1, valueConverter);
	}

	public ExcelRowIteratorHandler(final File file, final int skipHeaderRowsSize) {
		super((r, c, v) -> v);
		this.file = file;
		this.skipHeaderRowsSize = skipHeaderRowsSize;
	}

	public ExcelRowIteratorHandler(final File file) {
		this(file, 1);
	}

	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		return new ExcelIterator(c, file, 0L, this.getRowValueConverter(), this.skipHeaderRowsSize);
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c, final int index) {
		return new ExcelIterator(c, file, index, this.getRowValueConverter(), this.skipHeaderRowsSize);
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c) {
		return (ListIterator<Row>) iterator(c);
	}

	public static class ExcelIterator extends AbstractRowListIterator<org.apache.poi.ss.usermodel.Row> {

		ExcelIterator(final RowCollection c, final File file, final long index, final RowValueConverter valueConverter,
				final int skipHeaderRowsSize) {
			super(c, index, valueConverter);
			this.file = file;
			this.filename = file.getAbsolutePath();
			this.skipHeaderRowsSize = skipHeaderRowsSize;
		}

		private final File file;
		private Workbook workbook;
		private final String filename;
		private final int skipHeaderRowsSize;

		private final Map<Number, Column> columnIndexColumnMap = CommonUtils.map();

		private final Map<Number, Boolean> columnIndexFixedTypeMap = CommonUtils.map();

		private Iterator<org.apache.poi.ss.usermodel.Row> rowIterator;

		@Override
		protected void preInitialize() throws Exception {
			this.workbook = WorkbookFileType.createWorkBook(file, null, true);
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

		protected void initializeColumn2() throws Exception {
			if (skipHeaderRowsSize == 1) {
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
					if (columnIndexColumnMap.isEmpty()) {
						setColumnTypeMap(table);
					}
				}
			} else {
				for (int i = 0; i < skipHeaderRowsSize; i++) {
					read();
				}
				setColumnTypeMap(table);
			}
		}

		private void setColumnTypeMap(final Table table) {
			int i = 0;
			for (final Column column : table.getColumns()) {
				if (column.getDataType() != null) {
					columnIndexFixedTypeMap.put(i, true);
				}
				columnIndexColumnMap.put(i, column);
				i++;
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
