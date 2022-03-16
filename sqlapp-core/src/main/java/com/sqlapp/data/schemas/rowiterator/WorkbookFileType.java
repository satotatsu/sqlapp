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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvListWriter;
import org.supercsv.io.ICsvListReader;
import org.supercsv.io.ICsvListWriter;
import org.supercsv.prefs.CsvPreference;

import com.sqlapp.util.file.FileType;

public enum WorkbookFileType {
	EXCEL2003(){
		@Override
		public String getFileExtension(){
			return "xls";
		}
		@Override
		public HSSFWorkbook createWorkbook(){
			return new HSSFWorkbook();
		}
		@Override
		public boolean isWorkbook(){
			return true;
		}
	},
	EXCEL2007(){
		@Override
		public String getFileExtension(){
			return "xlsx";
		}
		@Override
		public Workbook createWorkbook(){
			return new XSSFWorkbook();
		}
		@Override
		public boolean isWorkbook(){
			return true;
		}
	}
	, CALC(){
		@Override
		public String getFileExtension(){
			return "ods";
		}
		@Override
		public SXSSFWorkbook createWorkbook(){
			return new SXSSFWorkbook();
		}
		@Override
		public boolean isWorkbook(){
			return true;
		}
	}
	, TSV(){
		@Override
		public String getFileExtension(){
			return "tsv";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isCsv(){
			return true;
		}
		@Override
		public ICsvListWriter createCsvListWriter(final Writer writer){
			return new CsvListWriter(writer, CsvPreference.TAB_PREFERENCE);
		}
		@Override
		public ICsvListReader createCsvListReader(final Reader reader){
			return new CsvListReader(reader, CsvPreference.TAB_PREFERENCE);
		}
		@Override
		public FileType getFileType() {
			return FileType.TSV;
		}
	}
	, CSV(){
		@Override
		public String getFileExtension(){
			return "csv";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isCsv(){
			return true;
		}
		@Override
		public ICsvListWriter createCsvListWriter(final Writer writer){
			return new CsvListWriter(writer, CsvPreference.EXCEL_PREFERENCE);
		}
		@Override
		public ICsvListReader createCsvListReader(final Reader reader){
			return new CsvListReader(reader, CsvPreference.EXCEL_PREFERENCE);
		}
		@Override
		public FileType getFileType() {
			return FileType.CSV;
		}
	}
	, SSV(){
		@Override
		public String getFileExtension(){
			return "ssv";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isCsv(){
			return true;
		}
		@Override
		public ICsvListWriter createCsvListWriter(final Writer writer){
			return new CsvListWriter(writer, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
		}
		@Override
		public ICsvListReader createCsvListReader(final Reader reader){
			return new CsvListReader(reader, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
		}
		@Override
		public FileType getFileType() {
			return FileType.SSV;
		}
	}
	, XML(){
		@Override
		public String getFileExtension(){
			return "xml";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isXml(){
			return true;
		}
	}
	, JSON(){
		@Override
		public String getFileExtension(){
			return "json";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isJson(){
			return true;
		}
	}
	, JSONL(){
		@Override
		public String getFileExtension(){
			return "jsonl";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isJsonl(){
			return true;
		}
	}
	, YAML(){
		@Override
		public String getFileExtension(){
			return "yaml";
		}
		@Override
		public boolean isTextFile(){
			return true;
		}
		@Override
		public boolean isYaml(){
			return true;
		}
	}
	,;
	
	public String getFileExtension(){
		return null;
	}

	public boolean isJson(){
		return false;
	}

	public boolean isJsonl(){
		return false;
	}

	public boolean isTextFile(){
		return false;
	}

	public boolean isCsv(){
		return false;
	}

	public boolean isXml(){
		return false;
	}

	public boolean isYaml(){
		return false;
	}
	
	public boolean isWorkbook(){
		return false;
	}

	/**
	 * ワークブックを作成します。
	 */
	public Workbook createWorkbook(){
		return null;
	}

	public FileType getFileType() {
		return null;
	}

	/**
	 * CSV List Writerを作成します。
	 */
	public ICsvListWriter createCsvListWriter(final Writer writer){
		return null;
	}

	/**
	 * CSV List Writerを作成します。
	 * @throws IOException 
	 */
	public ICsvListWriter createCsvListWriter(final File file, final String charset) throws IOException{
		return createCsvListWriter(new BufferedWriter(new FileWriter(file, Charset.forName(charset!=null?charset:"UTF8"))));
	}
	/**
	 * CSV List Readerを作成します。
	 * @throws IOException 
	 */
	public ICsvListReader createCsvListReader(final File file, final String charset) throws IOException{
		return createCsvListReader(new BufferedReader(new FileReader(file, Charset.forName(charset!=null?charset:"UTF8"))));
	}

	/**
	 * CSV List Readerを作成します。
	 */
	public ICsvListReader createCsvListReader(final Reader reader){
		return null;
	}
	
	public static Workbook createWorkBook(final File file) throws EncryptedDocumentException, InvalidFormatException, IOException{
        if (file.length() == 0) {
        	final WorkbookFileType type=WorkbookFileType.parse(file);
        	final Workbook workbook=type.createWorkbook();
        	return workbook;
        }
		return WorkbookFactory.create(file, null, false);
	}

	public static Workbook createWorkBook(final File file, final String password, final boolean readonly) throws EncryptedDocumentException, InvalidFormatException, IOException{
		return WorkbookFactory.create(file, password, readonly);
	}

	public static Workbook createWorkBook(final InputStream is) throws EncryptedDocumentException, InvalidFormatException, IOException{
		return WorkbookFactory.create(is);
	}

	public static WorkbookFileType parse(final String text){
		if (text==null){
			return null;
		}
		final String lowername=text.toLowerCase();
		for(final WorkbookFileType val:values()){
			if (lowername.endsWith(val.getFileExtension())){
				return val;
			}
			if (text.equalsIgnoreCase(val.toString())){
				return val;
			}
		}
		return null;
	}

	public static WorkbookFileType parse(final File file){
		if (file==null){
			return null;
		}
		return parse(file.getAbsolutePath());
	}

	public static WorkbookFileType parse(final Path path){
		if (path==null){
			return null;
		}
		return parse(path.toFile().getAbsolutePath());
	}

}
