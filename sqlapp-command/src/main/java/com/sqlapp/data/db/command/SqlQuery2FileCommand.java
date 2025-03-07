/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.List;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.ColumnFunction;
import com.sqlapp.data.schemas.function.ColumnValueFunction;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.ResultSetRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.OutputTextBuilder;
import com.sqlapp.util.file.TextFileWriter;
/**
 * クエリを実行して結果を標準出力に出力します。
 * @author tatsuo satoh
 *
 */
public class SqlQuery2FileCommand extends AbstractSqlQueryCommand{
	private String outputFileCharset="UTF-8";

	private File outputFile=null;

	private ColumnFunction<String> headerFunction=c->c.getName();

	private ColumnValueFunction<Object, String> valueFunction=(column, value)->Converters.getDefault().convertString(value);

	private RowValueConverter rowValueConverter=(r,c,v)->v;
	
	@Override
	protected void outputTableData(final Dialect dialect, final Table table) {
		final OutputTextBuilder builder=new OutputTextBuilder();
		builder.append(table);
		FileUtils.writeText(outputFile, outputFileCharset, builder.toString());
	}

	@Override
	protected void outputTableData(final Dialect dialect, final Table table, final ResultSet resultSet) throws IOException, Exception {
		final WorkbookFileType workbookFileType=this.getOutputFormatType().getWorkbookFileType();
		if (workbookFileType!=null) {
			try(TextFileWriter csvListWriter=workbookFileType.createCsvListWriter(this.outputFile, this.getOutputFileCharset())){
				final List<String> headers=getHeaders(table);
				csvListWriter.writeHeader(headers.toArray(new String[0]));
				for(final Row row:table.getRows(new ResultSetRowIteratorHandler(resultSet, getRowValueConverter()))) {
					final List<String> values=getValues(table, row);
					csvListWriter.writeHeader(values.toArray(new String[0]));
				}
			}
		}
	}

	private List<String> getHeaders(final Table table){
		final List<String> list=CommonUtils.list();
		for(final Column column:table.getColumns()) {
			final String value=headerFunction.apply(column);
			list.add(value);
		}
		return list;
	}


	private List<String> getValues(final Table table, final Row row){
		final List<String> list=CommonUtils.list();
		for(final Column column:table.getColumns()) {
			final Object obj=row.get(column);
			final String value=valueFunction.apply(column, obj);
			list.add(value);
		}
		return list;
	}

	public File getOutputFile() {
		return outputFile;
	}

	public void setOutputFile(final File outputFile) {
		this.outputFile = outputFile;
	}

	public String getOutputFileCharset() {
		return outputFileCharset;
	}

	public void setOutputFileCharset(final String outputFileCharset) {
		this.outputFileCharset = outputFileCharset;
	}

	public ColumnFunction<String> getHeaderFunction() {
		return headerFunction;
	}

	public void setHeaderFunction(final ColumnFunction<String> headerFunction) {
		this.headerFunction = headerFunction;
	}

	public ColumnValueFunction<Object, String> getValueFunction() {
		return valueFunction;
	}

	public void setValueFunction(final ColumnValueFunction<Object, String> valueFunction) {
		this.valueFunction = valueFunction;
	}

	public RowValueConverter getRowValueConverter() {
		return rowValueConverter;
	}

	public void setRowValueConverter(final RowValueConverter rowValueConverter) {
		this.rowValueConverter = rowValueConverter;
	}
	
	
}
