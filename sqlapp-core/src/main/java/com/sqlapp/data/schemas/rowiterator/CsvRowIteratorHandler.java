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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.supercsv.io.ICsvListReader;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
/**
 * CSVの行のIterator
 * @author tatsuo satoh
 *
 */
public class CsvRowIteratorHandler extends AbstractRowIteratorHandler{

	private final File file;
	private final Path path;
	private final WorkbookFileType workbookFileType;
	private final String charset;
	private final Reader reader;
	private final int skipHeaderRowsSize;

	public CsvRowIteratorHandler(final File file, final String charset){
		this(file, charset, 1);
	}
	
	public CsvRowIteratorHandler(final File file, final String charset, final int skipHeaderRowsSize){
		super((r, c,v)->v);
		this.workbookFileType=WorkbookFileType.parse(file);
		this.file=file;
		this.path=null;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public CsvRowIteratorHandler(final Path path, final String charset){
		this(path,charset, 1);
	}

	public CsvRowIteratorHandler(final Path path, final String charset, final int skipHeaderRowsSize){
		super((r, c,v)->v);
		this.workbookFileType=WorkbookFileType.parse(path);
		this.file=null;
		this.path=path;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public CsvRowIteratorHandler(final File file, final String charset, final RowValueConverter valueConverter){
		this(file,charset, 1, valueConverter);
	}

	public CsvRowIteratorHandler(final File file, final String charset, final int skipHeaderRowsSize, final RowValueConverter valueConverter){
		super(valueConverter);
		this.workbookFileType=WorkbookFileType.parse(file);
		this.file=file;
		this.path=null;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public CsvRowIteratorHandler(final Reader reader, final WorkbookFileType workbookFileType){
		this(reader, 1, workbookFileType);
	}

	public CsvRowIteratorHandler(final Reader reader, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType){
		super((r, c,v)->v);
		this.workbookFileType=workbookFileType;
		this.file=null;
		this.path=null;
		this.charset=null;
		this.reader=reader;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	
	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		if (file!=null){
			return new CsvRowIterator(c, file, skipHeaderRowsSize, workbookFileType, charset, 0L, this.getRowValueConverter());
		}else if (path!=null){
			return new CsvRowIterator(c, path, skipHeaderRowsSize, workbookFileType, charset, 0L, this.getRowValueConverter());
		} else{
			return new CsvRowIterator(c, reader, skipHeaderRowsSize, workbookFileType, 0L, this.getRowValueConverter());
		}
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c, final int index) {
		if (file!=null){
			return new CsvRowIterator(c, file, skipHeaderRowsSize, workbookFileType, charset, index, this.getRowValueConverter());
		}else if (path!=null){
			return new CsvRowIterator(c, path, skipHeaderRowsSize, workbookFileType, charset, index, this.getRowValueConverter());
		} else{
			return new CsvRowIterator(c, reader, skipHeaderRowsSize, workbookFileType, index, this.getRowValueConverter());
		}
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c) {
		return (ListIterator<Row>)iterator(c);
	}

	public static class CsvRowIterator extends AbstractTextRowListIterator<List<String>> {
		CsvRowIterator(final RowCollection c, final Path path, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final String charset, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.workbookFileType=workbookFileType;
			this.file=null;
			this.path=path;
			this.charset=charset;
			this.filename=path.toFile().getName();
			this.skipHeaderRowsSize=skipHeaderRowsSize;
		}

		CsvRowIterator(final RowCollection c, final File file, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final String charset, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.workbookFileType=workbookFileType;
			this.file=file;
			this.path=null;
			this.charset=charset;
			this.filename=file.getAbsolutePath();
			this.skipHeaderRowsSize=skipHeaderRowsSize;
		}

		CsvRowIterator(final RowCollection c, final Reader reader, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.workbookFileType=workbookFileType;
			this.file=null;
			this.path=null;
			this.reader=reader;
			this.charset=null;
			this.filename=null;
			this.skipHeaderRowsSize=skipHeaderRowsSize;
		}

		private final File file;
		private final Path path;
		private final WorkbookFileType workbookFileType;
		private final String charset;
		private Reader reader;
		private ICsvListReader csvReader;
		private String filename;
		private List<Column> columns;
		private List<String> current=null;
		private final int skipHeaderRowsSize;
		
		@Override
		protected void preInitialize() throws Exception{
			if (file!=null){
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				this.csvReader=workbookFileType.createCsvListReader(reader);
				this.filename=file.getAbsolutePath();
			} else if (path!=null){
				reader = Files.newBufferedReader(path, Charset.forName(charset));
				this.csvReader=workbookFileType.createCsvListReader(reader);
				this.filename=path.toFile().getAbsolutePath();
			} else{
				if (reader instanceof BufferedReader){
					this.csvReader=workbookFileType.createCsvListReader(reader);
				} else{
					final BufferedReader br=new BufferedReader(reader);
					this.csvReader=workbookFileType.createCsvListReader(br);
				}
				this.filename=null;
			}
		}
		

		@Override
		protected void initializeColumn() throws Exception {
			if(skipHeaderRowsSize==1) {
				columns=CommonUtils.list();
				final List<String> list=csvReader.read();
				if (CommonUtils.isEmpty(table.getColumns())){
					for(final String text:list){
						final Column column=new Column(text);
						columns.add(column);
						table.getColumns().add(column);
					}
				} else{
					for(final String columnName:list){
						final Column column=table.getColumns().get(columnName);
						if (column==null){
							table.getColumns().get(columnName.toLowerCase());
						}
						if (column==null){
							table.getColumns().get(columnName.toUpperCase());
						}
						if (column!=null){
							columns.add(column);
						}
					}
				}
			} else {
				for(int i=0;i<skipHeaderRowsSize;i++) {
					final List<String> list=csvReader.read();
					if (list==null){
						return;
					}
				}
				columns=CommonUtils.list();
				for(final Column column:table.getColumns()){
					columns.add(column);
					table.getColumns().get(column.getName());
				}
			}
		}
		
		@Override
		protected boolean hasNextInternal() throws Exception{
			readInternal();
			return current!=null;
		}

		private boolean readed=false;
		
		protected List<String> readInternal() throws Exception {
			if (readed){
				return current;
			}
			current= csvReader.read();
			readed=true;
			return current;
		}
	
		@Override
		protected List<String> read() throws Exception {
			current= readInternal();
			readed=false;
			return current;
		}

		@Override
		protected void set(final List<String> val, final Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceRowNumber(csvReader.getLineNumber());
			int i=0;
			if (val==null){
				return;
			}
			for(final String text:val){
				if (i<columns.size()){
					final Column column=columns.get(i++);
					final DataType type=getDataType(text);
					if (type!=null){
						if (column.getDataType()==null){
							column.setDataType(type);
						} else{
							if (!column.getDataType().isCharacter()){
								if (!type.isBoolean()){
									if (column.getDataType()!=DataType.DOUBLE){
										column.setDataType(type);
									}
								}
							}
						}
					}
					final long len=getTypeLength(text);
					if (column.getLength()!=null){
						column.setLength(Math.max(len, column.getLength()));
					} else{
						column.setLength(len);
					}
					put(row, column, text);
				}
			}
		}

		@Override
		protected void doClose() {
			FileUtils.close(csvReader);
			FileUtils.close(reader);
			this.csvReader = null;
			this.reader = null;
		}

	}
	
}
