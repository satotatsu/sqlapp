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
package com.sqlapp.data.db.command.html;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.supercsv.io.ICsvListReader;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.exceptions.InvalidFileTypeException;
import com.sqlapp.exceptions.InvalidPropertyException;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.JsonConverter;

public abstract class AbstractSchemaFileCommand extends AbstractCommand{

	/**
	 * file
	 */
	private File targetFile;

	private File dictionaryFileDirectory=new File("./");

	
	private String dictionaryFileType="xml";
	
	/**csvFileCharset*/
	private String csvEncoding="UTF-8";

	private JsonConverter jsonConverter=new JsonConverter();
	
	private String[] keywords=new String[]{SchemaProperties.DISPLAY_NAME.getLabel(), SchemaProperties.DISPLAY_REMARKS.getLabel()};

	private Map<String, Integer> keywordsMap=CommonUtils.map();

	private Catalog catalog=null;
	
	/**
	 * @return the keywords
	 */
	public String[] getKeywords() {
		return keywords;
	}
	
	protected AbstractSchemaFileCommand(){
		jsonConverter.setIndentOutput(true);
		for(int i=0;i<this.getKeywords().length;i++){
			keywordsMap.put(keywords[i], i);
		}
	}
	
	@Override
	protected void doRun() {
		DbCommonObject<?> obj = null;
		if (this.getCatalog()==null){
			try {
				obj = SchemaUtils.readXml(targetFile);
			} catch (FileNotFoundException e) {
				throw new CommandException("targetFile="+targetFile, e);
			} catch (XMLStreamException e) {
				throw new CommandException("targetFile="+targetFile, e);
			} catch (IOException e) {
				throw new CommandException("targetFile="+targetFile, e);
			}
		} else{
			obj=this.getCatalog();
		}
		try {
			if (obj instanceof Schema){
				create(((Schema)obj).toCatalog());
			}else if (obj instanceof SchemaCollection){
				create(((SchemaCollection)obj).toCatalog());
			}else if (obj instanceof Catalog){
				create((Catalog)obj);
			}else{
				throw new IllegalArgumentException("targetFile type must be a Schema or SchemaCollection or Catalog");
			}
		} catch (Exception e) {
			throw new CommandException("targetFile="+targetFile, e);
		}
	}

	protected abstract void create(Catalog catalog) throws Exception;

	protected String getFullName(Object obj, boolean withSchemaName){
		String fullName=null;
		if (withSchemaName){
			fullName=HtmlUtils.objectFullName(obj);
		} else{
			fullName=HtmlUtils.objectFullNameWithoutSchemaName(obj);
		}
		return fullName;
	}
	
	protected String getName(Object obj){
		String name=((NameProperty<?>)obj).getName();
		return name;
	}
	
	protected File loadProperties(MenuDefinition menuDefinition, String type, Properties properties) throws Exception{
		String filename=menuDefinition.toString().toLowerCase();
		File[] files=this.getDictionaryFileDirectory().listFiles((d, name)->{
			return name.startsWith(filename+".");
		});
		if (CommonUtils.isEmpty(files)){
			return null;
		}
		if (files.length>1){
			throw new DuplicatePropertyFilesException(files);
		}
		File file=CommonUtils.first(files);
		if (file.exists()){
			try(InputStream is=new FileInputStream(file)){
				if (file.getAbsolutePath().endsWith(".properties")){
					properties.load(is);
				}else if (file.getAbsolutePath().endsWith(".xml")){
					properties.loadFromXML(is);
				} else{
					readOtherFiles(file, is, properties);
				}
			}
			List<MenuDefinition> menuDefinitions=menuDefinition.getNest();
			for(Map.Entry<Object, Object> entry:properties.entrySet()){
				int current=entry.getKey().toString().split("\\.").length;
				if (current>(menuDefinitions.size()+2)){
					throw new InvalidPropertyException(entry.getKey().toString(), entry.getValue());
				}
			}
		}
		return file;
	}

	private void readOtherFiles(File file, InputStream is, Properties properties) throws Exception{
		WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
		if (workbookFileType.isTextFile()&&workbookFileType.isCsv()){
			readCsvFile(workbookFileType, file, is, properties);
		}else if (workbookFileType.isWorkbook()){
			readWorkbookFile(workbookFileType, file, is, properties);
		}else if (workbookFileType.isJson()){
			readJsonFile(workbookFileType, file, is, properties);
		} else{
			throw new InvalidFileTypeException(file);
		}
	}

	private void readWorkbookFile(WorkbookFileType workbookFileType, File file, InputStream is, Properties properties) throws UnsupportedEncodingException, IOException, EncryptedDocumentException, InvalidFormatException{
		Workbook workbook=WorkbookFileType.createWorkBook(is);
		int numberOdSheets=workbook.getNumberOfSheets();
		for(int sheetNo=0;sheetNo<numberOdSheets;sheetNo++){
			Sheet sheet=workbook.getSheetAt(sheetNo);
			int rowIndex=sheet.getFirstRowNum();
			Row row=sheet.getRow(rowIndex);
			int lastRowNum=sheet.getLastRowNum();
			short lastCellNum=row.getLastCellNum();
			String[] headers=new String[lastCellNum+1];
			MenuDefinition[] headerDefs=new MenuDefinition[headers.length];
			int keywordCount=0;
			for(int i=0;i<headers.length;i++){
				String header=ExcelUtils.getStringCellValue(row.getCell(i));
				if (header==null){
					continue;
				}
				MenuDefinition def=MenuDefinition.parse(header);
				if (def!=null){
					headerDefs[i]=def;
					headers[i]=def.toString();
				} else{
					headers[i]=getKeywords()[keywordCount++];
				}
			}
			for(int i=rowIndex+1;i<=lastRowNum;i++){
				StringBuilder builder=new StringBuilder();
				row=sheet.getRow(i);
				for(int j=0;j<headers.length;j++){
					String value=ExcelUtils.getStringCellValue(row.getCell(j));
					if (value==null){
						value="";
					}
					MenuDefinition headerDef=headerDefs[j];
					if (headerDef!=null){
						if (!CommonUtils.isEmpty(value)){
							builder.append(value);
							builder.append(".");
						}
					} else{
						String header=headers[j];
						if (header!=null){
							properties.put(builder.toString()+header, value);
						}
					}
				}
			}
		}
	}

	private void readCsvFile(WorkbookFileType workbookFileType, File file, InputStream is, Properties properties) throws UnsupportedEncodingException, IOException{
		try(Reader reader = new InputStreamReader(is, this.getCsvEncoding())){
			BufferedReader br=new BufferedReader(reader);
			ICsvListReader csvListReader=workbookFileType.createCsvListReader(br);
			String[] headers=csvListReader.getHeader(true);
			MenuDefinition[] headerDefs=new MenuDefinition[headers.length];
			int keywordCount=0;
			for(int i=0;i<headers.length;i++){
				String header=headers[i];
				if (header==null){
					continue;
				}
				MenuDefinition def=MenuDefinition.parse(header);
				if (def!=null){
					headerDefs[i]=def;
					headers[i]=def.toString();
				} else{
					headers[i]=getKeywords()[keywordCount++];
				}
			}
			List<String> list=csvListReader.read();
			while(list!=null){
				list=csvListReader.read();
				String text=CommonUtils.first(list);
				if (CommonUtils.isEmpty(text)){
					continue;
				}
				if (text.startsWith("#")){
					continue;
				}
				for(int i=0;i<list.size();i++){
					StringBuilder builder=new StringBuilder();
					String value=list.get(i);
					if (value==null){
						value="";
					}
					if (i<headers.length){
						MenuDefinition headerDef=headerDefs[i];
						if (headerDef!=null){
							if (!CommonUtils.isEmpty(value)){
								builder.append(value);
								builder.append(".");
							}
						} else{
							String header=headers[i];
							if (header!=null){
								properties.put(builder.toString()+header, value);
							}
						}
					}
				}
			}
		}
	}
	
	private void readJsonFile(WorkbookFileType workbookFileType, File file, InputStream is, Properties properties) throws Exception{
		Object obj=getJsonConverter().fromJsonString(is, Object.class);
		if (obj instanceof Collection||obj.getClass().isArray()){
			AbstractIterator<Object> itr=new AbstractIterator<Object>(){
				@Override
				protected void handle(Object obj, int index) throws Exception {
					if (obj instanceof Map){
						 @SuppressWarnings("rawtypes")
						Map<String, String> map=toStringMap((Map)obj);
						properties.putAll(map);
					}
				}
			};
			itr.execute(obj);
		} else{
			 @SuppressWarnings("rawtypes")
			Map<String, String> map=toStringMap((Map)obj);
			properties.putAll(map);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> toStringMap(@SuppressWarnings("rawtypes") Map map){
		Map<String, String> result=CommonUtils.linkedMap();
		String path=null;
		map.forEach((k,v)->{
			toStringList(path, k.toString(), v, result);
		});
		return result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void toStringList(String path, String key, Object value, Map<String, String> result){
		String currentPath=createPath(path, key);
		if ((value instanceof String)||(value instanceof Number)||(value instanceof Boolean)){
			result.put(currentPath, value.toString());
			return;
		}else if (value instanceof Map){
			((Map)value).forEach((k,v)->{
				toStringList(currentPath, k.toString(), v, result);
			});
		}
	}
	
	private String createPath(String path, String key){
		if (CommonUtils.isEmpty(path)){
			path=key;
		} else{
			path=path+"."+key;
		}
		return path;
	}
	
	protected void setCatalog(Catalog catalog) {
		this.catalog = catalog;
	}

	public Catalog getCatalog() {
		return catalog;
	}

	/**
	 * @return the targetFile
	 */
	public File getTargetFile() {
		return targetFile;
	}

	/**
	 * @param targetFile the targetFile to set
	 */
	public void setTargetFile(File targetFile) {
		this.targetFile = targetFile;
	}


	/**
	 * @return the dictionaryFileDirectory
	 */
	public File getDictionaryFileDirectory() {
		return dictionaryFileDirectory;
	}

	/**
	 * @param dictionaryFileDirectory the dictionaryFileDirectory to set
	 */
	public void setDictionaryFileDirectory(File dictionaryFileDirectory) {
		this.dictionaryFileDirectory = dictionaryFileDirectory;
	}

	/**
	 * @return the dictionaryFileType
	 */
	public String getDictionaryFileType() {
		return dictionaryFileType;
	}

	/**
	 * @param dictionaryFileType the dictionaryFileType to set
	 */
	public void setDictionaryFileType(String dictionaryFileType) {
		this.dictionaryFileType = dictionaryFileType;
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

	/**
	 * @return the keywordsMap
	 */
	protected Map<String, Integer> getKeywordsMap() {
		return keywordsMap;
	}


}
