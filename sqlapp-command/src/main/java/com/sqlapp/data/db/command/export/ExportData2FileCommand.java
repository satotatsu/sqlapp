/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.supercsv.io.ICsvListWriter;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableNameRowCollectionFilter;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;

/**
 * Exportコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class ExportData2FileCommand extends AbstractExportCommand {
	/**
	 * Export対象が指定されなかった場合のExportをデフォルトとする
	 */
	private boolean defaultExport=false;
	/**
	 * Output File Type
	 */
	private WorkbookFileType outputFileType=WorkbookFileType.EXCEL2007;
	/**
	 * SELECT ALLのWHERE条件のオプション
	 */
	private Options options=null;

	private String sheetName="TABLE";
	
	private final Converters converters =new Converters();
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@Override
	protected void doRun() {
		final Dialect dialect=this.getDialect();
		SchemaReader schemaReader=null;
		try {
			schemaReader = getSchemaReader(dialect);
		} catch (final SQLException e) {
			this.getExceptionHandler().handle(e);
		}
		final Map<String, Schema> schemaMap=this.getSchemas(dialect, schemaReader);
		final RowIteratorHandler rowIteratorHandler = getRowIteratorHandler();
		schemaMap.forEach((k,v)->{
			v.setRowIteratorHandler(rowIteratorHandler);
		});
		if (!this.getDirectory().exists()){
			FileUtils.createParentDirectory(this.getDirectory());
			this.getDirectory().mkdir();
		}
		final DoubleKeyMap<String,String,Table> execTables=CommonUtils.doubleKeyMap();
		schemaMap.forEach((k,v)->{
			File targetDirectory=null;
			if (this.isUseSchemaNameDirectory()){
				final File file=new File(this.getDirectory(), k);
				if (!file.exists()){
					file.mkdirs();
					file.mkdir();
				}
				targetDirectory=file;
			}else{
				targetDirectory=this.getDirectory();
			}
			for(final Table t:v.getTables()){
				try {
					writeTable(targetDirectory, t.getName(), t, this.getOutputFileType());
					execTables.put(t.getSchemaName(), t.getName(), t);
				} catch (final FileNotFoundException e) {
					this.getExceptionHandler().handle(e);
				} catch (final EncryptedDocumentException e) {
					this.getExceptionHandler().handle(e);
				} catch (final XMLStreamException e) {
					this.getExceptionHandler().handle(e);
				} catch (final InvalidFormatException e) {
					this.getExceptionHandler().handle(e);
				} catch (final IOException e) {
					this.getExceptionHandler().handle(e);
				}
			}
			for(final Synonym s:v.getSynonyms()){
				final Table table=s.rootSynonym().getTable();
				if (table==null){
					continue;
				}
				if (execTables.containsKey(table.getSchemaName(), table.getName())){
					continue;
				}
				try {
					writeTable(targetDirectory, s.getName(), table, this.getOutputFileType());
				} catch (final FileNotFoundException e) {
					this.getExceptionHandler().handle(e);
				} catch (final EncryptedDocumentException e) {
					this.getExceptionHandler().handle(e);
				} catch (final XMLStreamException e) {
					this.getExceptionHandler().handle(e);
				} catch (final InvalidFormatException e) {
					this.getExceptionHandler().handle(e);
				} catch (final IOException e) {
					this.getExceptionHandler().handle(e);
				}
			}
		});
	}
	
	private void writeTable(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws FileNotFoundException, IOException, EncryptedDocumentException, InvalidFormatException, XMLStreamException{
		if (this.getOutputFileType().isTextFile()){
			if (this.getOutputFileType().isCsv()){
				writeTableAsCsv(directory, filename, table, this.getOutputFileType());
			}else if (this.getOutputFileType().isXml()){
				writeTableAsXml(directory, filename, table, this.getOutputFileType());
			}else if (this.getOutputFileType().isJson()){
				writeTableAsJson(directory, filename, table, this.getOutputFileType());
			}else if (this.getOutputFileType().isJsonl()){
				writeTableAsJsonl(directory, filename, table, this.getOutputFileType());
			}else if (this.getOutputFileType().isYaml()){
				writeTableAsYaml(directory, filename, table, this.getOutputFileType());
			}
		} else{
			writeTableAsExcel(directory, filename, table, this.getOutputFileType());
		}
	}

	@SuppressWarnings("unchecked")
	private void writeTableAsCsv(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws IOException{
		final File file=new File(directory, filename+"."+workbookFileType.getFileExtension());
		try(FileOutputStream fos = new FileOutputStream(file);
			OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
			BufferedWriter bw=new BufferedWriter(writer);
			ICsvListWriter csvWriter=workbookFileType.createCsvListWriter(bw)){
			final List<String> headers=table.getColumns().stream().map(c->c.getName()).collect(Collectors.toList());
			csvWriter.writeHeader(headers.toArray(new String[0]));
			final String[] values=new String[table.getColumns().size()];
			for(final Row row:table.getRows()){
				int i=0;
				for(final Column column:table.getColumns()){
					final Object value=row.get(column);
					values[i++]=column.getConverter().convertString(value);
				}
				csvWriter.write(values);
			}
		}
	}

	private void writeTableAsXml(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws IOException, XMLStreamException{
		final File file=new File(directory, filename+"."+workbookFileType.getFileExtension());
		table.writeRowData(file);
	}

	private void writeTableAsJson(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws IOException, XMLStreamException{
		final File file=new File(directory, filename+"."+workbookFileType.getFileExtension());
		try(FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw=new BufferedWriter(writer);){
			bw.write("[");
			boolean first=true;
			for(final Row row:table.getRows()){
				final String text=getJsonConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				if (!first){
					bw.write(",\n");
				} else{
					bw.write("\n");
					first=false;
				}
				bw.write(text);
			}
			bw.write("]");
		}
	}

	private void writeTableAsJsonl(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws IOException, XMLStreamException{
		final File file=new File(directory, filename+"."+workbookFileType.getFileExtension());
		final JsonConverter converter=getJsonConverter().clone();
		converter.setIndentOutput(false);
		try(FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw=new BufferedWriter(writer);){
			for(final Row row:table.getRows()){
				final String text=getJsonConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				bw.write("\n");
				bw.write(text);
			}
		}
	}

	private void writeTableAsYaml(final File directory, final String filename, final Table table, final WorkbookFileType workbookFileType) throws IOException, XMLStreamException{
		final File file=new File(directory, filename+"."+workbookFileType.getFileExtension());
		try(FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw=new BufferedWriter(writer);){
			for(final Row row:table.getRows()){
				final String text=getYamlConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				bw.write(text);
			}
		}
	}

	private void writeTableAsExcel(final File directory, final String fileName, final Table table, final WorkbookFileType workbookFileType) throws FileNotFoundException, IOException, EncryptedDocumentException, InvalidFormatException{
		final File file=new File(directory, fileName+"."+workbookFileType.getFileExtension());
		Workbook workbook;
		Sheet sheet;
		if (file.exists()){
			workbook = WorkbookFileType.createWorkBook(file);
			sheet=ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
			ExcelUtils.clearCellValues(sheet);
		} else{
			workbook = workbookFileType.createWorkbook();
			sheet=ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
		}
		int rownum=0;
		final org.apache.poi.ss.usermodel.Row headerRow
			=ExcelUtils.getOrCreateRow(sheet, rownum++);
		int cellnum=0;
		final CreationHelper helper = workbook.getCreationHelper();
		for(final Column column:table.getColumns()){
			final Cell cell=ExcelUtils.getOrCreateCell(headerRow, cellnum++);
			ExcelUtils.setCell(converters, workbook, cell, column.getName());
		}
		for(final Row row:table.getRows()){
			final org.apache.poi.ss.usermodel.Row dataRow
				=ExcelUtils.getOrCreateRow(sheet, rownum++);
			cellnum=0;
			for(final Column column:table.getColumns()){
				final Object obj=row.get(column);
				if (obj!=null){
					final Cell cell=ExcelUtils.getOrCreateCell(dataRow, cellnum);
					ExcelUtils.setCell(converters, workbook, cell, obj);
				}
				cellnum++;
			}
		}
		cellnum=0;
		for(final Column column:table.getColumns()){
			sheet.autoSizeColumn(cellnum);
			if (column.getRemarks()!=null){
				final Cell cell=ExcelUtils.getOrCreateCell(headerRow, cellnum);
				ExcelUtils.setComment(helper, cell, column.getRemarks());
			}
			cellnum++;
		}
		ExcelUtils.writeWorkbook(workbook, file);
	}

	protected RowIteratorHandler getRowIteratorHandler() {
		final JdbcDynamicRowIteratorHandler rowIteratorHandler = new JdbcDynamicRowIteratorHandler();
		rowIteratorHandler.setDataSource(this.getDataSource());
		rowIteratorHandler.setOptions(this.getOptions());
		final TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
		filter.setIncludes(this.getIncludeTables());
		filter.setExcludes(this.getExcludeTables());
		filter.setDefaultInclude(this.isDefaultExport());
		rowIteratorHandler.setFilter(filter);
		return rowIteratorHandler;
	}

	/**
	 * @return the outputFileType
	 */
	public WorkbookFileType getOutputFileType() {
		return outputFileType;
	}

	/**
	 * @param outputFileType the outputFileType to set
	 */
	public void setOutputFileType(final WorkbookFileType outputFileType) {
		this.outputFileType = outputFileType;
	}

	/**
	 * @return the options
	 */
	public Options getOptions() {
		return options;
	}

	/**
	 * @param options the options to set
	 */
	public void setOption(final Options options) {
		this.options = options;
	}

	/**
	 * @return the defaultExport
	 */
	public boolean isDefaultExport() {
		return defaultExport;
	}

	/**
	 * @param defaultExport the defaultExport to set
	 */
	public void setDefaultExport(final boolean defaultExport) {
		this.defaultExport = defaultExport;
	}

	/**
	 * @return the sheetName
	 */
	public String getSheetName() {
		return sheetName;
	}

	/**
	 * @param sheetName the sheetName to set
	 */
	public void setSheetName(final String sheetName) {
		this.sheetName = sheetName;
	}


}
