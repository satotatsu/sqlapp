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
 * @author tatsuo satoh
 *
 */
public class ExcelRowIteratorHandler extends AbstractRowIteratorHandler{

	private final File file;

	
	public ExcelRowIteratorHandler(File file, RowValueConverter valueConverter){
		super(valueConverter);
		this.file=file;
	}

	public ExcelRowIteratorHandler(File file){
		super((r, c,v)->v);
		this.file=file;
	}

	@Override
	public Iterator<Row> iterator(RowCollection c) {
		return new ExcelIterator(c, file, 0L, this.getRowValueConverter());
	}

	@Override
	public ListIterator<Row> listIterator(RowCollection c, int index) {
		return new ExcelIterator(c, file, index, this.getRowValueConverter());
	}

	@Override
	public ListIterator<Row> listIterator(RowCollection c) {
		return (ListIterator<Row>)iterator(c);
	}

	public static class ExcelIterator extends AbstractRowListIterator<org.apache.poi.ss.usermodel.Row> {
		
		ExcelIterator(RowCollection c, File file, long index, RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.file=file;
			this.filename=file.getAbsolutePath();
		}

		private final File file;
		private Workbook workbook;
		private String filename;
		
		Map<Number, Column> columnIndexColumnMap=CommonUtils.map();
		
		Map<Number, Boolean> columnIndexFixedTypeMap=CommonUtils.map();
		
		private Iterator<org.apache.poi.ss.usermodel.Row> rowIterator;

		@Override
		protected void preInitialize() throws Exception {
			this.workbook=WorkbookFileType.createWorkBook(file);
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
		protected void initializeColumn() throws IOException{
			Sheet sheet=workbook.getSheetAt(0);
			rowIterator=sheet.rowIterator();
			org.apache.poi.ss.usermodel.Row headerRow;
			if (hasNextInternal()){
				headerRow=read();
			} else{
				return;
			}
			if (CommonUtils.isEmpty(table.getColumns())){
				headerRow.forEach(cell->{
					Object obj=ExcelUtils.getCellValue(cell);
					if (!(obj instanceof String)){
						return;
					}
					String columnName=(String)obj;
					Column column=new Column(columnName);
					columnIndexColumnMap.put(cell.getColumnIndex(), column);
					columnIndexFixedTypeMap.put(cell.getColumnIndex(), false);
					table.getColumns().add(column);
				});
			} else{
				boolean[] hasType=new boolean[1];
				hasType[0]=true;
				headerRow.forEach(cell->{
					Object obj=ExcelUtils.getCellValue(cell);
					if (!(obj instanceof String)){
						return;
					}
					String columnName=(String)obj;
					Column column=table.getColumns().get(columnName);
					if (column==null){
						table.getColumns().get(columnName.toLowerCase());
					}
					if (column==null){
						table.getColumns().get(columnName.toUpperCase());
					}
					columnIndexFixedTypeMap.put(cell.getColumnIndex(), false);
					if (column!=null){
						if (column.getDataType()!=null){
							columnIndexFixedTypeMap.put(cell.getColumnIndex(), true);
						}
						columnIndexColumnMap.put(cell.getColumnIndex(), column);
					}
				});
			}
		}

		@Override
		protected void set(org.apache.poi.ss.usermodel.Row excelRow, Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceDetailInfo(excelRow.getSheet().getSheetName());
			row.setDataSourceRowNumber(excelRow.getRowNum()+1);
			excelRow.forEach(cell->{
				Column column=columnIndexColumnMap.get(cell.getColumnIndex());
				if (column==null){
					return;
				}
				Boolean fixed=columnIndexFixedTypeMap.get(cell.getColumnIndex());
				Object value=ExcelUtils.getCellValue(cell);
				if (value!=null){
					if (!fixed.booleanValue()){
						ExcelUtils.setColumnType(cell, column);
						if (value instanceof String){
							if (column.getLength()!=null){
								column.setLength(Math.max(this.getTypeLength((String)value), column.getLength()));
							} else{
								column.setLength(this.getTypeLength((String)value));
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
				if (workbook!=null){
					workbook.close();
				}
			} catch (IOException e) {
			}
		}
		
	}
	
}
