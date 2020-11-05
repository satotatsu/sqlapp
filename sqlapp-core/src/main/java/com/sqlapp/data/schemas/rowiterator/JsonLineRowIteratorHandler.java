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
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler.JsonRowIterator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;
/**
 * JSON line Iterator
 * @author tatsuo satoh
 *
 */
public class JsonLineRowIteratorHandler extends AbstractRowIteratorHandler{

	private final File file;
	private final JsonConverter jsonConverter;

	
	public JsonLineRowIteratorHandler(final File file, final JsonConverter jsonConverter, final RowValueConverter valueConverter){
		super(valueConverter);
		this.file=file;
		this.jsonConverter=jsonConverter;
	}

	public JsonLineRowIteratorHandler(final File file, final JsonConverter jsonConverter){
		super((r, c, v)->v);
		this.file=file;
		this.jsonConverter=jsonConverter;
	}

	public JsonLineRowIteratorHandler(final File file){
		super((r, c, v)->v);
		this.file=file;
		this.jsonConverter=new JsonConverter();
	}

	
	@Override
	public Iterator<Row> iterator(final RowCollection c) {
		return new JsonRowIterator(c, file, jsonConverter, 0L, this.getRowValueConverter());
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c, final int index) {
		return new JsonRowIterator(c, file, jsonConverter, index, this.getRowValueConverter());
	}

	@Override
	public ListIterator<Row> listIterator(final RowCollection c) {
		return (ListIterator<Row>)iterator(c);
	}

	public static class JsonlineRowIterator extends AbstractRowListIterator<Map<String,Object>> {
		JsonlineRowIterator(final RowCollection c, final File file, final JsonConverter jsonConverter, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.file=file;
			this.jsonConverter=jsonConverter;
		}

		JsonlineRowIterator(final RowCollection c, final Reader reader, final JsonConverter jsonConverter, final long index, final RowValueConverter valueConverter){
			super(c, index, valueConverter);
			this.file=null;
			this.jsonConverter=jsonConverter;
			if (reader instanceof BufferedReader) {
				this.reader=(BufferedReader)reader;
			} else {
				this.reader=new BufferedReader(reader);
			}
		}

		private final JsonConverter jsonConverter;

		private final File file;
		private final String charset="utf8";
		private BufferedReader reader;
		private String filename;
		private Map<String,Object> current=null;
		private boolean hasColumn=false;

		@Override
		protected void preInitialize() throws Exception{
			if (file!=null){
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
				this.filename=file.getAbsolutePath();
			} else{
				this.filename=null;
			}
		}


		@Override
		protected void initializeColumn() throws Exception {
			if (!CommonUtils.isEmpty(table.getColumns())){
				hasColumn=true;
			}
		}
		@Override
		protected boolean hasNextInternal() throws Exception{
			readInternal();
			return current!=null;
		}

		private boolean readed=false;
		
		@SuppressWarnings("unchecked")
		protected Map<String,Object> readInternal() throws Exception {
			if (readed){
				return current;
			}
			final String value=reader.readLine();
			if (!CommonUtils.isEmpty(value)) {
				current=this.jsonConverter.fromJsonString(value, Map.class);
			}
			readed=true;
			return current;
		}
	
		@Override
		protected Map<String,Object> read() throws Exception {
			current= readInternal();
			readed=false;
			return current;
		}
		
		
		@Override
		protected void set(final Map<String, Object> map, final Row row) throws Exception {
			row.setDataSourceInfo(filename);
			row.setDataSourceRowNumber(count+1);
			map.forEach((columnName,value)->{
				Column column=table.getColumns().get(columnName);
				if (column==null){
					table.getColumns().get(columnName.toLowerCase());
				}
				if (column==null){
					table.getColumns().get(columnName.toUpperCase());
				}
				if (!hasColumn){
					if (column==null){
						column=new Column(columnName);
						table.getColumns().add(column);
					}
				}
				if (column!=null){
					if (value!=null){
						setType(column, value);
						put(row, column, value);
					}
				}
			});
		}
		
		private void setType(final Column column, final Object value){
			if (value instanceof Boolean){
				if (column.getDataType()==null){
					column.setDataType(DataType.BOOLEAN);
				}
			} else if (value instanceof Integer||value instanceof Long){
				if (column.getDataType()==null){
					column.setDataType(DataType.BIGINT);
				}
			} else if (value instanceof Number){
				if (column.getDataType()==null){
					column.setDataType(DataType.DECIMAL);
					column.setLength(38);
					column.setScale(17);
				}else if (column.getDataType()==DataType.INT){
					column.setDataType(DataType.DECIMAL);
					column.setLength(38);
					column.setScale(17);
				}
			} else{
				final String val=value.toString();
				final long len=getTypeLength(val);
				if (column.getDataType()==null){
					column.setDataType(DataType.NVARCHAR);
					column.setLength(len);
				}
				if (column.getLength()!=null){
					column.setLength(Math.max(len, column.getLength()));
				}
			}
		}
		
		@Override
		protected void doClose() {
			FileUtils.close(this.reader);
		}
	}
	
}
