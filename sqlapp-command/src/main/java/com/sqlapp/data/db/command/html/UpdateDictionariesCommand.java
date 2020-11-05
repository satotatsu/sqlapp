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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.supercsv.io.ICsvListWriter;

import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.RemarksProperty;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.exceptions.InvalidFileTypeException;
import com.sqlapp.exceptions.InvalidPropertyException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.LinkedProperties;

public class UpdateDictionariesCommand extends AbstractSchemaFileCommand{

	private Predicate<String> withSchema=(o)->true;

	private boolean outputRemarksAsDisplayName=true;
	
	@Override
	protected void create(Catalog catalog){
		try {
			for(MenuDefinition menuDefinition:MenuDefinition.values()){
				createProperties(catalog, menuDefinition, (obj)->HtmlUtils.objectFullName(obj));
			}
		} catch (Exception e) {
			this.getExceptionHandler().handle(e);
		}
	}

	protected void createProperties(Catalog catalog, MenuDefinition menuDefinition, Function<Object, String> nameFunc) throws Exception{
		List<AbstractDbObject<?>> list=menuDefinition.getDatas(catalog);
		if (list.isEmpty()){
			return;
		}
		if (!(list.get(0) instanceof NameProperty)){
			return;
		}
		String filename=menuDefinition.toString().toLowerCase();
		//Properties properties=new SortedProperties(new StringComparator(this.keywordsMap));
		Properties fileProperties=new LinkedProperties();
		Properties mergeProperties=new LinkedProperties();
		File file=loadProperties(menuDefinition, this.getDictionaryFileType(), fileProperties);
		list.forEach(obj->{
			String fullName=this.getFullName(obj, this.getWithSchema().test(filename));
			putProperty(fileProperties, fullName, obj, mergeProperties);
		});
		Set<String> keys=CommonUtils.treeSet();
		if (menuDefinition==MenuDefinition.Columns){
			list.forEach(obj->{
				String fullName=this.getFullName(obj, this.getWithSchema().test(filename));
				String columnNameWithTable=this.getColumnNameWithTable(obj);
				if (!CommonUtils.eq(fullName, columnNameWithTable)){
					putProperty(fileProperties, columnNameWithTable, obj, mergeProperties);
				}
			});
			list.forEach(obj->{
				String fullName=this.getFullName(obj, this.getWithSchema().test(filename));
				String columnNameWithTable=this.getColumnNameWithTable(obj);
				String name=this.getName(obj);
				if (!CommonUtils.eq(fullName, name)&&!CommonUtils.eq(columnNameWithTable, name)){
					keys.add(name);
				}
			});
		} else{
			list.forEach(obj->{
				String fullName=this.getFullName(obj, this.getWithSchema().test(filename));
				String name=this.getName(obj);
				if (!CommonUtils.eq(fullName, name)){
					keys.add(name);
				}
			});
		}
		keys.forEach(k->{
			putProperty(fileProperties, k, null, mergeProperties);
		});
		if (file==null){
			file=new File(this.getDictionaryFileDirectory(), filename+"."+this.getDictionaryFileType());
		}
		FileUtils.createParentDirectory(file);
		writeProperties(file, menuDefinition, mergeProperties);
	}

	private String getColumnNameWithTable(AbstractDbObject<?> obj){
		Column column=(Column)obj;
		return column.getTableName()+"."+column.getName();
	}
	
	private void writeProperties(File file, MenuDefinition menuDefinition, Properties properties) throws FileNotFoundException, IOException{
		File outputFile=new File(file.getParentFile(), FileUtils.getFileNameWithoutExtension(file.getAbsolutePath())+"."+this.getDictionaryFileType());
		File tempFile=File.createTempFile(FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()), "."+this.getDictionaryFileType(), file.getParentFile());
		try{
			if ("properties".equalsIgnoreCase(this.getDictionaryFileType())){
				try(OutputStream os=new FileOutputStream(tempFile)){
					properties.store(os, menuDefinition+" dictionaries");
				}
			}else if ("xml".equalsIgnoreCase(this.getDictionaryFileType())){
				try(OutputStream os=new FileOutputStream(tempFile)){
					properties.storeToXML(os, menuDefinition+" dictionaries");
				}
				tempFile.renameTo(outputFile);
				if (!outputFile.equals(file)){
					FileUtils.remove(file);
				}
			} else{
				writeOtherProperties(tempFile, menuDefinition, properties);
			}
		} finally{
			if (outputFile.exists()){
				outputFile.delete();
			}
			tempFile.renameTo(outputFile);
		}
	}

	private void writeOtherProperties(File file, MenuDefinition menuDefinition, Properties properties) throws IOException{
		WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
		if (workbookFileType.isTextFile()&&workbookFileType.isCsv()){
			writeAsCsv(workbookFileType, file, menuDefinition, properties);
		}else if (workbookFileType.isWorkbook()){
			writeAsWorkbook(workbookFileType, file, menuDefinition, properties);
		}else if (workbookFileType.isJson()){
			writeAsJson(workbookFileType, file, menuDefinition, properties);
		} else{
			throw new InvalidFileTypeException(file);
		}
	}
	
	private void writeAsCsv(WorkbookFileType workbookFileType, File file, MenuDefinition menuDefinition, Properties properties) throws IOException{
		List<MenuDefinition> menuDefinitions=CommonUtils.list(menuDefinition.getNest());
		int maxNestLebel=getMaxNestLebel(properties);
		if (maxNestLebel!=menuDefinition.getNestLevel()){
			menuDefinitions.remove(0);
		}
		try(FileOutputStream fos = new FileOutputStream(file);
			OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
			BufferedWriter bw=new BufferedWriter(writer);
			ICsvListWriter csvWriter=workbookFileType.createCsvListWriter(bw)){
			List<String> headers=menuDefinitions.stream().map(c->c.toString()).collect(Collectors.toList());
			for(String keyword:getKeywords()){
				headers.add(keyword);
			}
			csvWriter.writeHeader(headers.toArray(new String[0]));
			Set<String> output=CommonUtils.set();
			for(Map.Entry<Object, Object> entry:properties.entrySet()){
				String key=entry.getKey().toString();
				if (output.contains(key)){
					continue;
				}
				String[] values=new String[headers.size()];
				String value=(String)entry.getValue();
				int pos=key.lastIndexOf('.');
				if (pos<0){
					throw new InvalidPropertyException(key, value);
				}
				String suffix=key.substring(pos+1);
				String[] args=key.split("\\.");
				for(int i=0;i<headers.size();i++){
					values[i]="";
					String header=headers.get(i);
					if (i<headers.size()-2){
						values[i]=getHeaderValue(headers, i, args);
					} else{
						if (suffix.equalsIgnoreCase(header)){
							values[i]=value;
							output.add(key);
						} else{
							String otherKey=key.substring(0, pos)+"."+header;
							output.add(otherKey);
							Object otherValue=properties.getProperty(otherKey);
							if (otherValue!=null){
								values[i]=otherValue.toString();
							}
						}
					}
				}
				csvWriter.write(values);
			}
		}
	}

	private void writeAsWorkbook(WorkbookFileType workbookFileType, File file, MenuDefinition menuDefinition, Properties properties) throws IOException{
		List<MenuDefinition> menuDefinitions=CommonUtils.list(menuDefinition.getNest());
		int maxNestLebel=getMaxNestLebel(properties);
		if (maxNestLebel!=menuDefinition.getNestLevel()){
			menuDefinitions.remove(0);
		}
		try(FileOutputStream fos = new FileOutputStream(file)){
			Workbook workbook=workbookFileType.createWorkbook();
			Sheet sheet=workbook.createSheet(menuDefinition.toString());
			List<String> headers=menuDefinitions.stream().map(c->c.toString()).collect(Collectors.toList());
			for(String keyword:getKeywords()){
				headers.add(keyword);
			}
			int rowNo=0;
			org.apache.poi.ss.usermodel.Row row=ExcelUtils.getOrCreateRow(sheet, rowNo++);
			int cellNo=0;
			for(String header:headers){
				Cell cell=ExcelUtils.getOrCreateCell(row, cellNo++);
				cell.setCellValue(header);
			}
			Set<String> output=CommonUtils.set();
			for(Map.Entry<Object, Object> entry:properties.entrySet()){
				String key=entry.getKey().toString();
				if (output.contains(key)){
					continue;
				}
				String[] values=new String[headers.size()];
				String value=(String)entry.getValue();
				int pos=key.lastIndexOf('.');
				if (pos<0){
					throw new InvalidPropertyException(key, value);
				}
				String suffix=key.substring(pos+1);
				String[] args=key.split("\\.");
				for(int i=0;i<headers.size();i++){
					values[i]="";
					String header=headers.get(i);
					if (i<headers.size()-2){
						values[i]=getHeaderValue(headers, i, args);
					} else{
						if (suffix.equalsIgnoreCase(header)){
							values[i]=value;
							output.add(key);
						} else{
							String otherKey=key.substring(0, pos)+"."+header;
							output.add(otherKey);
							Object otherValue=properties.getProperty(otherKey);
							if (otherValue!=null){
								values[i]=otherValue.toString();
							}
						}
					}
				}
				row=ExcelUtils.getOrCreateRow(sheet, rowNo++);
				cellNo=0;
				for(String val:values){
					Cell cell=ExcelUtils.getOrCreateCell(row, cellNo++);
					cell.setCellValue(val);
				}
			}
			for(int i=0;i<headers.size();i++){
				sheet.autoSizeColumn(i);
			}
			workbook.write(fos);
		}
	}
	
	private void writeAsJson(WorkbookFileType workbookFileType, File file, MenuDefinition menuDefinition, Properties properties) throws IOException{
		try(FileOutputStream fos = new FileOutputStream(file);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));){
			Map<String, Object> map=CommonUtils.linkedMap();
			for(Map.Entry<Object, Object> entry:properties.entrySet()){
				String key=entry.getKey().toString();
				String value=(String)entry.getValue();
				int pos=key.lastIndexOf('.');
				String[] args=key.split("\\.");
				String suffix=key.substring(pos+1);
				if (!SchemaProperties.DISPLAY_NAME.getLabel().equalsIgnoreCase(suffix)){
					continue;
				}
				putValue(map, args, value);
			}
			for(Map.Entry<Object, Object> entry:properties.entrySet()){
				String key=entry.getKey().toString();
				String value=(String)entry.getValue();
				int pos=key.lastIndexOf('.');
				String[] args=key.split("\\.");
				String suffix=key.substring(pos+1);
				if (SchemaProperties.DISPLAY_NAME.getLabel().equalsIgnoreCase(suffix)){
					continue;
				}
				putValue(map, args, value);
			}
			String text=this.getJsonConverter().toJsonString(map);
			bw.write(text);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void putValue(Map<String, Object> map, String[] args, String value){
		if (args.length==1&&getKeywordsMap().containsKey(args[0])){
			return;
		}
		for(int i=0;i<args.length;i++){
			String arg=args[i];
			if (i==args.length-1){
				map.put(arg, value);
			} else{
				Object obj=map.get(arg);
				if (obj==null){
					Map<String, Object> child=CommonUtils.linkedMap();
					map.put(arg, child);
					map=child;
				} else{
					map=(Map<String, Object>)obj;
				}
			}
			
		}
	}
	
	private String getHeaderValue(List<String> headers, int i, String[] args){
		int diff=headers.size()-2-(args.length-1);
		if (i>=diff){
			return args[i-diff];
		} else{
			return "";
		}
	}

	private int getMaxNestLebel(Properties properties){
		int level=0;
		for(Object key:properties.keySet()){
			int current=key.toString().split("\\.").length;
			level=Math.max(level, current-1);
		}
		return level;
	}

	private void putProperty(Properties fileProperties, String name, AbstractDbObject<?> obj, Properties mergeProperties){
		for(String keyword:getKeywords()){
			String key=name+"."+keyword;
			String value=fileProperties.getProperty(key);
			if(!CommonUtils.isEmpty(value)){
				mergeProperties.put(key, value);
			} else{
				if (this.isOutputRemarksAsDisplayName()){
					if (SchemaProperties.DISPLAY_NAME.getLabel().equals(keyword)){
						if (obj instanceof RemarksProperty){
							RemarksProperty<?> remarksProperty=(RemarksProperty<?>)obj;
							value=remarksProperty.getRemarks();
						}
					}
				}
				if (value!=null){
					mergeProperties.put(key, value);
				} else{
					mergeProperties.put(key, "");
				}
			}
		}
	}
	
	static class StringComparator implements Comparator<String>{

		private Map<String, Integer> keywordsMap;
		
		StringComparator(Map<String, Integer> keywordsMap){
			this.keywordsMap=keywordsMap;
		}
		
		@Override
		public int compare(String o1, String o2) {
			String[] split1=o1.split("\\.");
			String[] split2=o2.split("\\.");
			if (split1.length>split2.length){
				return 1;
			} else if (split1.length<split2.length){
				return -1;
			}
			int comp=compareWithoutLast(split1, split2);
			if (comp!=0){
				return comp;
			}
			return compareLast(split1, split2);
		}
		
		private int compareWithoutLast(String[] split1, String[] split2){
			for(int i=0;i<split1.length-1;i++){
				int comp=split1[i].compareTo(split2[i]);
				if (comp!=0){
					return comp;
				}
			}
			return 0;
		}

		private int compareLast(String[] split1, String[] split2){
			String value1=split1[split1.length-1];
			String value2=split2[split2.length-1];
			Integer int1=keywordsMap.get(value1);
			Integer int2=keywordsMap.get(value2);
			if (int1==null){
				if (int2==null){
					return value1.compareTo(value2);
				} else{
					return 1;
				}
			} else{
				if (int2==null){
					return 0;
				} else{
					return int1.compareTo(int2);
				}
			}
		}
	}

	/**
	 * @return the withSchema
	 */
	public Predicate<String> getWithSchema() {
		return withSchema;
	}

	/**
	 * @return the outputRemarksAsDisplayName
	 */
	public boolean isOutputRemarksAsDisplayName() {
		return outputRemarksAsDisplayName;
	}

	/**
	 * @param outputRemarksAsDisplayName the outputRemarksAsDisplayName to set
	 */
	public void setOutputRemarksAsDisplayName(boolean outputRemarksAsDisplayName) {
		this.outputRemarksAsDisplayName = outputRemarksAsDisplayName;
	}

	/**
	 * @param withSchema the withSchema to set
	 */
	public void setWithSchema(Predicate<String> withSchema) {
		this.withSchema = withSchema;
	}

}
