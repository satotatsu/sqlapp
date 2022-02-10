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
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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
import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.CsvRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.ExcelRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;

/**
 * Excel,CSV,Jsonのファイルを相互変換するためのコマンド
 * @author tatsuo satoh
 *
 */
public class ConvertDataFileCommand extends AbstractCommand{
	
	/**file filter*/
	private Predicate<File> fileFilter=f->true;
	/**
	 * Output Direcroty
	 */
	private File directory=new File(".");

	private String csvEncoding=Charset.defaultCharset().toString();

	private JsonConverter jsonConverter=createJsonConverter();
	
	private boolean recursive=false;

	private String sheetName="TABLE";
	/**
	 * Output File Type
	 */
	private WorkbookFileType outputFileType=WorkbookFileType.EXCEL2007;
	
	private Converters converters =new Converters();
	
	private boolean removeOriginalFile=false;
	
	/**
	 * Output Direcroty
	 */
	private File outputDirectory=null;

	public ConvertDataFileCommand(){
	}
	
	@Override
	protected void doRun() {
		List<File> list=getFiles();
		for(File file:list){
			WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
			RowIteratorHandler rowIteratorHandler;
			Table table=new Table();
			if (workbookFileType.isWorkbook()){
				rowIteratorHandler=new ExcelRowIteratorHandler(file);
				table.setRowIteratorHandler(rowIteratorHandler);
			} else if (workbookFileType.isCsv()){
				rowIteratorHandler=new CsvRowIteratorHandler(file, this.getCsvEncoding());
				table.setRowIteratorHandler(rowIteratorHandler);
			} else if (workbookFileType.isJson()){
				rowIteratorHandler=new JsonRowIteratorHandler(file, this.getJsonConverter());
				table.setRowIteratorHandler(rowIteratorHandler);
			} else{
				try {
					table=SchemaUtils.readXml(file);
				} catch (Exception e) {
					this.getExceptionHandler().handle(new CommandException("file="+file.getAbsolutePath(), e));
				}
			}
			File tempFile=null;
			try {
				File outputFile;
				if (this.getOutputDirectory()!=null&&!CommonUtils.eq(this.getOutputDirectory(), this.getDirectory())&&!CommonUtils.eq(this.getOutputDirectory(), file.getParentFile())){
					String path=file.getParentFile().getAbsolutePath();
					path=FileUtils.combinePath(this.getOutputDirectory().getAbsolutePath(), path.substring(this.getDirectory().getAbsolutePath().length()));
					File parent= new File(path);
					if (!parent.exists()){
						parent.mkdirs();
					}
					tempFile=File.createTempFile(FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()), "."+this.getOutputFileType().getFileExtension(), parent);
					outputFile=new File(parent, FileUtils.getFileNameWithoutExtension(file.getAbsolutePath())+"."+this.getOutputFileType().getFileExtension());
				} else{
					tempFile=File.createTempFile(FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()), "."+this.getOutputFileType().getFileExtension(), file.getParentFile());
					outputFile=new File(file.getParentFile(), FileUtils.getFileNameWithoutExtension(file.getAbsolutePath())+"."+this.getOutputFileType().getFileExtension());
				}
				if (this.getOutputFileType().isWorkbook()){
					readAll(table);
					writeTableAsExcel(tempFile, table, this.getOutputFileType());
				} else if (this.getOutputFileType().isCsv()){
					readAll(table);
					writeTableAsCsv(tempFile, table, this.getOutputFileType());
				} else if (this.getOutputFileType().isJson()){
					writeTableAsJson(tempFile, table, this.getOutputFileType());
				} else{
					table.writeXml(tempFile);
				}
				tempFile.renameTo(outputFile);
				if (this.isRemoveOriginalFile()){
					file.delete();
				}
			} catch (Exception e) {
				if (tempFile!=null&&tempFile.exists()){
					tempFile.delete();
				}
				this.getExceptionHandler().handle(new CommandException("file="+file.getAbsolutePath(), e));
			}
		}
	}
	
	private void readAll(Table table){
		for(@SuppressWarnings("unused") Row row:table.getRows()){
			
		}
	}
	
	@SuppressWarnings("unchecked")
	private void writeTableAsCsv(File file, Table table, WorkbookFileType workbookFileType) throws IOException{
		try(FileOutputStream fos = new FileOutputStream(file);
			OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
			BufferedWriter bw=new BufferedWriter(writer);
			ICsvListWriter csvWriter=workbookFileType.createCsvListWriter(bw)){
			List<String> headers=table.getColumns().stream().map(c->c.getName()).collect(Collectors.toList());
			csvWriter.writeHeader(headers.toArray(new String[0]));
			String[] values=new String[table.getColumns().size()];
			for(Row row:table.getRows()){
				int i=0;
				boolean set=false;
				for(Column column:table.getColumns()){
					Object value=row.get(column);
					String text=column.getConverter().convertString(value);
					if (!CommonUtils.isEmpty(text)){
						values[i++]=text;
						set=true;
					}
				}
				if (set){
					csvWriter.write(values);
				}
			}
		}
	}
	
	private void writeTableAsJson(File file, Table table, WorkbookFileType workbookFileType) throws IOException, XMLStreamException{
		try(FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw=new BufferedWriter(writer);){
			bw.write("[");
			boolean first=true;
			for(Row row:table.getRows()){
				Map<String,Object> map=row.getValuesAsMapWithoutNullValue();
				if (map.isEmpty()){
					continue;
				}
				String text=getJsonConverter().toJsonString(map);
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
	
	private void writeTableAsExcel(File file, Table table, WorkbookFileType workbookFileType) throws FileNotFoundException, IOException, EncryptedDocumentException, InvalidFormatException{
		Workbook workbook = workbookFileType.createWorkbook();
		Sheet sheet=ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
		int rownum=0;
		org.apache.poi.ss.usermodel.Row headerRow
			=ExcelUtils.getOrCreateRow(sheet, rownum++);
		int cellnum=0;
		CreationHelper helper = workbook.getCreationHelper();
		for(Column column:table.getColumns()){
			Cell cell=ExcelUtils.getOrCreateCell(headerRow, cellnum++);
			ExcelUtils.setCell(getConverters(), workbook, cell, column.getName());
		}
		for(Row row:table.getRows()){
			org.apache.poi.ss.usermodel.Row dataRow
				=ExcelUtils.getOrCreateRow(sheet, rownum++);
			cellnum=0;
			for(Column column:table.getColumns()){
				Object obj=row.get(column);
				if (obj!=null){
					Cell cell=ExcelUtils.getOrCreateCell(dataRow, cellnum);
					ExcelUtils.setCell(getConverters(), workbook, cell, obj);
				}
				cellnum++;
			}
		}
		cellnum=0;
		for(Column column:table.getColumns()){
			sheet.autoSizeColumn(cellnum);
			if (column.getRemarks()!=null){
				Cell cell=ExcelUtils.getOrCreateCell(headerRow, cellnum);
				ExcelUtils.setComment(helper, cell, column.getRemarks());
			}
			cellnum++;
		}
		ExcelUtils.writeWorkbook(workbook, file);
	}
	
	private List<File> getFiles(){
		List<File> list=CommonUtils.list();
		findFiles(this.getDirectory(), list);
		return list;
	}

	private void findFiles(File file, List<File> list){
		if (!file.exists()){
			return;
		}
		if(file.isDirectory()){
			File[] children=file.listFiles(f->true);
			if (children!=null){
				for(File child:children){
					if (child.isFile()){
						addFile(child, list);
					} else{
						if (isRecursive()){
							findFiles(child, list);
						}
					}
				}
			}
		} else{
			addFile(file, list);
		}
	}

	private void addFile(File file, List<File> list){
		if (!file.exists()){
			return;
		}
		WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
		if (workbookFileType!=null&&workbookFileType!=this.getOutputFileType()){
			if (fileFilter.test(file)){
				list.add(file);
			}
		}
	}
	
	/**
	 * @return the recursive
	 */
	public boolean isRecursive() {
		return recursive;
	}

	/**
	 * @param recursive the recursive to set
	 */
	public void setRecursive(boolean recursive) {
		this.recursive = recursive;
	}

	/**
	 * @return the fileFilter
	 */
	public Predicate<File> getFileFilter() {
		return fileFilter;
	}

	/**
	 * @param fileFilter the fileFilter to set
	 */
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
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
	public void setOutputFileType(WorkbookFileType outputFileType) {
		this.outputFileType = outputFileType;
	}

	/**
	 * @return the converters
	 */
	protected Converters getConverters() {
		return converters;
	}

	/**
	 * @param converters the converters to set
	 */
	protected void setConverters(Converters converters) {
		this.converters = converters;
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
	public void setSheetName(String sheetName) {
		this.sheetName = sheetName;
	}

	/**
	 * @return the removeOriginalFile
	 */
	public boolean isRemoveOriginalFile() {
		return removeOriginalFile;
	}

	/**
	 * @param removeOriginalFile the removeOriginalFile to set
	 */
	public void setRemoveOriginalFile(boolean removeOriginalFile) {
		this.removeOriginalFile = removeOriginalFile;
	}

	/**
	 * @return the outputDirectory
	 */
	public File getOutputDirectory() {
		return outputDirectory;
	}

	/**
	 * @param outputDirectory the outputDirectory to set
	 */
	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	/**
	 * @return the directory
	 */
	public File getDirectory() {
		return directory;
	}

	/**
	 * @param directory the directory to set
	 */
	public void setDirectory(File directory) {
		this.directory = directory;
	}

	/**
	 * @return the csvEncoding
	 */
	public String getCsvEncoding() {
		return csvEncoding;
	}

	/**
	 * @param csvEncoding the csvEncoding to set
	 */
	public void setCsvEncoding(String csvEncoding) {
		this.csvEncoding = csvEncoding;
	}

	/**
	 * @return the jsonConverter
	 */
	public JsonConverter getJsonConverter() {
		return jsonConverter;
	}

	/**
	 * @param jsonConverter the jsonConverter to set
	 */
	public void setJsonConverter(JsonConverter jsonConverter) {
		this.jsonConverter = jsonConverter;
	}

}
