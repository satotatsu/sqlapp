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
import java.util.function.Consumer;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.file.AbstractFileParser;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.common.Format;
/**
 * CSVの行のIterator
 * @author tatsuo satoh
 *
 */
public class FileTypeRowIteratorHandler extends AbstractRowIteratorHandler{

	private final File file;
	private final Path path;
	private final WorkbookFileType workbookFileType;
	private final String charset;
	private final Reader reader;
	private final int skipHeaderRowsSize;

	public FileTypeRowIteratorHandler(final File file, final String charset){
		this(file, charset, 1);
	}
	
	public FileTypeRowIteratorHandler(final File file, final String charset, final int skipHeaderRowsSize){
		super((r, c,v)->v);
		this.workbookFileType=WorkbookFileType.parse(file);
		this.file=file;
		this.path=null;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public FileTypeRowIteratorHandler(final Path path, final String charset){
		this(path,charset, 1);
	}

	public FileTypeRowIteratorHandler(final Path path, final String charset, final int skipHeaderRowsSize){
		super((r, c,v)->v);
		this.workbookFileType=WorkbookFileType.parse(path);
		this.file=null;
		this.path=path;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public FileTypeRowIteratorHandler(final File file, final String charset, final RowValueConverter valueConverter){
		this(file,charset, 1, valueConverter);
	}

	public FileTypeRowIteratorHandler(final File file, final String charset, final int skipHeaderRowsSize, final RowValueConverter valueConverter){
		super(valueConverter);
		this.workbookFileType=WorkbookFileType.parse(file);
		this.file=file;
		this.path=null;
		this.charset=charset;
		this.reader=null;
		this.skipHeaderRowsSize=skipHeaderRowsSize;
	}

	public FileTypeRowIteratorHandler(final Reader reader, final WorkbookFileType workbookFileType){
		this(reader, 1, workbookFileType);
	}

	public FileTypeRowIteratorHandler(final Reader reader, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType){
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
			return new FileTypeRowIterator(c, file, skipHeaderRowsSize, workbookFileType, charset, 0L, this.getRowValueConverter());
		}else if (path!=null){
			return new FileTypeRowIterator(c, path, skipHeaderRowsSize, workbookFileType, charset, 0L, this.getRowValueConverter());
		} else{
			return new FileTypeRowIterator(c, reader, skipHeaderRowsSize, workbookFileType, 0L, this.getRowValueConverter());
		}
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c, final int index) {
		if (file!=null){
			return new FileTypeRowIterator(c, file, skipHeaderRowsSize, workbookFileType, charset, index, this.getRowValueConverter());
		}else if (path!=null){
			return new FileTypeRowIterator(c, path, skipHeaderRowsSize, workbookFileType, charset, index, this.getRowValueConverter());
		} else{
			return new FileTypeRowIterator(c, reader, skipHeaderRowsSize, workbookFileType, index, this.getRowValueConverter());
		}
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c) {
		return (ListIterator<Row>)iterator(c);
	}

	public static class FileTypeRowIterator extends AbstractTextRowListIterator<String[]> {
		FileTypeRowIterator(final RowCollection c, final Path path, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final String charset, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.workbookFileType=workbookFileType;
			this.file=null;
			this.path=path;
			this.charset=charset;
			this.filename=path.toFile().getName();
			this.skipHeaderRowsSize=skipHeaderRowsSize;
		}

		FileTypeRowIterator(final RowCollection c, final File file, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final String charset, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.workbookFileType=workbookFileType;
			this.file=file;
			this.path=null;
			this.charset=charset;
			this.filename=file.getAbsolutePath();
			this.skipHeaderRowsSize=skipHeaderRowsSize;
		}

		FileTypeRowIterator(final RowCollection c, final Reader reader, final int skipHeaderRowsSize, final WorkbookFileType workbookFileType, final long index, final RowValueConverter valueConverter){
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
		private AbstractFileParser<?,?> fileReader;
		private String filename;
		private List<ColumnPosition> columns;
		private String[] current=null;
		private final int skipHeaderRowsSize;
		
		private final Consumer<CommonParserSettings<? extends Format>> settingConsumer=s->{};
		
		@Override
		protected void preInitialize() throws Exception{
			if (file!=null){
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				this.fileReader=workbookFileType.getFileType().createParser(file, charset, settingConsumer);
				this.filename=file.getAbsolutePath();
			} else if (path!=null){
				reader = Files.newBufferedReader(path, Charset.forName(charset));
				this.fileReader=workbookFileType.getFileType().createParser(reader, settingConsumer);
				this.filename=path.toFile().getAbsolutePath();
			} else{
				this.fileReader=workbookFileType.getFileType().createParser(reader, settingConsumer);
				this.filename=null;
			}
			this.fileReader.beginParsing();
		}
		

		@Override
		protected void initializeColumn() throws Exception {
			if(skipHeaderRowsSize==1) {
				columns=CommonUtils.list();
				final String[] list=fileReader.parseNext();
				if (CommonUtils.isEmpty(table.getColumns())){
					int i=0;
					for(final String columnName:list){
						final Column column=new Column(columnName);
						columns.add(new ColumnPosition(i, column));
						table.getColumns().add(column);
						i++;
					}
				} else{
					int i=0;
					for(final String columnName:list){
						final Column column=searchColumn(table, columnName);
						if (column!=null){
							columns.add(new ColumnPosition(i, column));
						} else {
							columns.add(new ColumnPosition(i, null));
						}
						i++;
					}
					if (columns.isEmpty()) {
						i=0;
						for(final Column column:table.getColumns()){
							columns.add(new ColumnPosition(i, column));
							i++;
						}
					}
				}
			} else {
				for(int i=0;i<skipHeaderRowsSize;i++) {
					final String[] list=fileReader.parseNext();
					if (list==null){
						return;
					}
				}
				columns=CommonUtils.list();
				int i=0;
				for(final Column column:table.getColumns()){
					columns.add(new ColumnPosition(i, column));
					i++;
				}
			}
		}
		
		@Override
		protected boolean hasNextInternal() throws Exception{
			readInternal();
			return current!=null;
		}

		private boolean readed=false;
		
		protected String[] readInternal() throws Exception {
			if (readed){
				return current;
			}
			current= fileReader.parseNext();
			readed=true;
			return current;
		}
	
		@Override
		protected String[] read() throws Exception {
			current= readInternal();
			readed=false;
			return current;
		}

		@Override
		protected void set(final String[] val, final Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceRowNumber(fileReader.getLineNumber());
			int i=0;
			if (val==null){
				return;
			}
			
			for(final String text:val){
				if (i<columns.size()){
					final ColumnPosition columnPosition=columns.get(i++);
					final Column column=columnPosition.column;
					if (column==null) {
						continue;
					}
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
			FileUtils.close(reader);
			this.fileReader.close();
			this.reader = null;
		}

	}

	static class ColumnPosition {
		public final int index;
		public final Column column;

		ColumnPosition(final int index, final Column column) {
			this.index = index;
			this.column = column;
		}
	}
}
