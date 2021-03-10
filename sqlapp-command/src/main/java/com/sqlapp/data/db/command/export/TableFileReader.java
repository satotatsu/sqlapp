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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.CombinedRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.CsvRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.ExcelRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.data.schemas.rowiterator.XmlRowIteratorHandler;
import com.sqlapp.exceptions.InvalidValueException;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;

public class TableFileReader {
	/**
	 * data file Direcroty
	 */
	private File directory=null;
	/**
	 * data file
	 */
	private File file=null;

	private boolean useSchemaNameDirectory=false;

	private String csvEncoding=Charset.defaultCharset().toString();

	private int csvSkipHeaderRowsSize=1;

	private boolean useTableNameDirectory=false;

	private JsonConverter jsonConverter=createJsonConverter();
	/**file directory*/
	private File fileDirectory=null;
	/**file filter*/
	private Predicate<File> fileFilter=f->true;

	private String placeholderPrefix="${";

	private String placeholderSuffix="}";

	private boolean placeholders=false;
	
	private Map<String,Object> context=CommonUtils.linkedMap();

	public TableFileReader(){
	}
	
	public List<TableFilesPair> getTableFilePairs(final Catalog catalog) {
		final Set<String> schemaNames=CommonUtils.lowerSet();
		if (this.getFile()!=null&&this.getFile().isFile()) {
			final List<TableFilesPair> tfs=CommonUtils.list();
			catalog.getSchemas().forEach(s->{
				s.getTables().forEach(t->{
					final TableFilesPair tf=new TableFilesPair(t, this.getFile());
					tfs.add(tf);
				});
			});
			return tfs;
		}
		if (isUseSchemaNameDirectory()){
			final File[] directories=getDirectory().listFiles(c->c.isDirectory());
			if (directories!=null) {
				for(final File directory:directories){
					final String name=directory.getName();
					schemaNames.add(name);
				}
			}
		}
		final List<TableFilePair> tableFilePairs;
		if (isUseSchemaNameDirectory()){
			final File[] directories=getDirectory().listFiles(c->c.isDirectory());
			tableFilePairs=CommonUtils.list();
			if (directories!=null) {
				for(final File directory:directories){
					final Schema schema=catalog.getSchemas().get(directory.getName());
					if (schema!=null){
						tableFilePairs.addAll(getTableFilePairs(directory, schema));
					}
				}
			}
		} else{
			tableFilePairs=getTableFilePairs(getDirectory(), catalog.getSchemas());
		}
		final List<TableFilesPair> tfs=toTableFilesPairs(tableFilePairs);
		final Set<Table> tables=tfs.stream().map(tf->tf.getTable()).collect(Collectors.toSet());
		final List<Table> notExists=CommonUtils.list();
		catalog.getSchemas().forEach(s->{
			s.getTables().forEach(t->{
				if (!tables.contains(t)) {
					notExists.add(t);
				}
			});
		});
		notExists.forEach(t->{
			final TableFilesPair tf=new TableFilesPair(t, Collections.emptyList());
			tfs.add(tf);
		});
		return tfs;
	}
	
	public void setFiles(final List<TableFilesPair> tfs) throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException {
		for(final TableFilesPair tf:tfs){
			readFiles(tf.getTable(), tf.getFiles());
		}
	}
	
	private List<TableFilesPair> toTableFilesPairs(final List<TableFilePair> tableFilePairs){
		final Map<Table, List<File>> tableFileMap=CommonUtils.linkedMap();
		tableFilePairs.forEach(c->{
			List<File> files=tableFileMap.get(c.getTable());
			if (files==null){
				files=CommonUtils.list();
				tableFileMap.put(c.getTable(), files);
			}
			files.add(c.getFile());
		});
		final List<Table> tables=CommonUtils.list(tableFileMap.keySet());
		final List<TableFilesPair> result=tables.stream().map(t->{
			final List<File> files=tableFileMap.get(t);
			final TableFilesPair tfs=new TableFilesPair(t, files);
			return tfs;
		}).collect(Collectors.toList());
		return result;
	}
	
	public static class TableFilesPair{
		TableFilesPair(final Table table, final List<File> files){
			this.table=table;
			this.files=files;
		}
		TableFilesPair(final Table table, final File... files){
			this.table=table;
			this.files=CommonUtils.list(files);
		}
		private final Table table;
		private final List<File> files;
		
		/**
		 * @return the table
		 */
		public Table getTable() {
			return table;
		}

		/**
		 * @return the files
		 */
		public List<File> getFiles() {
			return files;
		}
		
		@Override
		public String toString(){
			final StringBuilder builder=new StringBuilder();
			builder.append("[");
			builder.append(table.getName());
			builder.append(", ");
			builder.append("files=");
			builder.append(files);
			builder.append("]");
			return builder.toString();
		}
	}
	
	static class TableFilePair{

		TableFilePair(final Table table){
			this.table=table;
			this.synonym=null;
			this.name=table.getName();
		}

		TableFilePair(final Synonym synonym){
			this.synonym=synonym;
			this.table=synonym.rootSynonym().getTable();
			this.name=synonym.getName();
		}

		private final Table table;
		private final Synonym synonym;
		private File file;
		private final String name;
		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}
		/**
		 * @return the synonym
		 */
		public Synonym getSynonym() {
			return synonym;
		}
		/**
		 * @return the table
		 */
		public Table getTable() {
			return table;
		}
		
		
		/**
		 * @param file the file to set
		 */
		public void setFile(final File file) {
			this.file = file;
		}
		/**
		 * @return the file
		 */
		public File getFile() {
			return file;
		}
	}

	private List<TableFilePair> getTableFilePairs(final File directory, final SchemaCollection schemas){
		final Map<String,TableFilePair> tables=CommonUtils.linkedMap();
		schemas.forEach(schema->{
			schema.getTables().forEach(t->{
				final TableFilePair pair=new TableFilePair(t);
				tables.put(t.getName(), pair);
			});
			schema.getSynonyms().forEach(t->{
				final TableFilePair pair=new TableFilePair(t);
				if (!tables.containsKey(t.getName())){
					tables.put(t.getName(), pair);
				}
			});
		});
		File[] files=null;
		if (directory!=null&&directory.exists()) {
			files=directory.listFiles();
		}
		final List<TableFilePair> result=getTableFilePairWithFile((name)->tables.get(name), files);
		return result;
	}

	private List<TableFilePair> getTableFilePairs(final File directory, final Schema schema){
		return getTableFilePairWithFile((name)->{
			final Table table=schema.getTables().get(name);
			if (table!=null){
				final TableFilePair pair=new TableFilePair(table);
				return pair;
			}
			final Synonym synonym=schema.getSynonyms().get(name);
			if (synonym!=null){
				final TableFilePair pair=new TableFilePair(synonym);
				return pair;
			}
			return null;
		}, directory.listFiles());
	}
	
	private List<TableFilePair> getTableFilePairWithFile(final Function<String, TableFilePair> func, final File... files){
		final List<TableFilePair> result=CommonUtils.list();
		if (files==null){
			return result;
		}
		final List<File> fs=Arrays.stream(files).filter(f->f.isFile())
				.filter(f->WorkbookFileType.parse(f)!=null)
				.filter(f->fileFilter.test(f)).collect(Collectors.toList());
		for(final File file:fs){
			final String name=FileUtils.getFileNameWithoutExtension(file);
			final TableFilePair pair=func.apply(name);
			if (pair!=null){
				pair.setFile(file);
				result.add(pair);
			}
		}
		return result;
	}
	
	private SqlConverter getSqlConverter(){
		final SqlConverter sqlConverter=new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}

	private void readFiles(final Table table, final List<File> files) throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException{
		final List<RowIteratorHandler> handlers=files.stream().map(file->{
			final WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
			if (workbookFileType.isTextFile()){
				if (workbookFileType.isCsv()){
					return new CsvRowIteratorHandler(file, getCsvEncoding(), getCsvSkipHeaderRowsSize(), getRowValueConverter());
				} else if (workbookFileType.isXml()){
					return new XmlRowIteratorHandler(file, getRowValueConverter());
				} else{
					return new JsonRowIteratorHandler(file, this.getJsonConverter(), getRowValueConverter());
				}
			} else{
				return new ExcelRowIteratorHandler(file, getRowValueConverter());
			}
		}).collect(Collectors.toList());
		if (!handlers.isEmpty()){
			table.setRowIteratorHandler(new CombinedRowIteratorHandler(handlers));
		}
	}

	private RowValueConverter getRowValueConverter(){
		final SqlConverter sqlConverter=getSqlConverter();
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		return (r, c, v)->{
			Object val;
			try {
				val = sqlConverter.getExpressionConverter().convert(v, context);
			} catch (final IOException e) {
				throw new InvalidValueException(r, c, v, e);
			}
			return val;
		};
	}

	/**
	 * @return the useTableNameDirectory
	 */
	public boolean isUseTableNameDirectory() {
		return useTableNameDirectory;
	}

	/**
	 * @param useTableNameDirectory the useTableNameDirectory to set
	 */
	public void setUseTableNameDirectory(final boolean useTableNameDirectory) {
		this.useTableNameDirectory = useTableNameDirectory;
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
	public void setDirectory(final File directory) {
		this.directory = directory;
	}

	public File getFile() {
		return file;
	}

	public void setFile(final File file) {
		this.file = file;
	}

	/**
	 * @return the useSchemaNameDirectory
	 */
	public boolean isUseSchemaNameDirectory() {
		return useSchemaNameDirectory;
	}

	/**
	 * @param useSchemaNameDirectory the useSchemaNameDirectory to set
	 */
	public void setUseSchemaNameDirectory(final boolean useSchemaNameDirectory) {
		this.useSchemaNameDirectory = useSchemaNameDirectory;
	}

	/**
	 * @return the fileDirectory
	 */
	public File getFileDirectory() {
		return fileDirectory;
	}

	/**
	 * @param fileDirectory the fileDirectory to set
	 */
	public void setFileDirectory(final File fileDirectory) {
		this.fileDirectory = fileDirectory;
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
	public void setFileFilter(final Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	/**
	 * @return the placeholderPrefix
	 */
	public String getPlaceholderPrefix() {
		return placeholderPrefix;
	}

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	public void setPlaceholderPrefix(final String placeholderPrefix) {
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * @return the placeholderSuffix
	 */
	public String getPlaceholderSuffix() {
		return placeholderSuffix;
	}

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	public void setPlaceholderSuffix(final String placeholderSuffix) {
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * @return the placeholders
	 */
	public boolean isPlaceholders() {
		return placeholders;
	}

	/**
	 * @param placeholders the placeholders to set
	 */
	public void setPlaceholders(final boolean placeholders) {
		this.placeholders = placeholders;
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
	public void setJsonConverter(final JsonConverter jsonConverter) {
		this.jsonConverter = jsonConverter;
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
	public void setCsvEncoding(final String csvEncoding) {
		this.csvEncoding = csvEncoding;
	}

	public int getCsvSkipHeaderRowsSize() {
		return csvSkipHeaderRowsSize;
	}

	public void setCsvSkipHeaderRowsSize(final int csvSkipHeaderRowsSize) {
		this.csvSkipHeaderRowsSize = csvSkipHeaderRowsSize;
	}

	/**
	 * @return the context
	 */
	public Map<String, Object> getContext() {
		return context;
	}

	/**
	 * @param context the context to set
	 */
	public void setContext(final Map<String, Object> context) {
		this.context = context;
	}

	private JsonConverter createJsonConverter(){
		final JsonConverter jsonConverter=new JsonConverter();
		jsonConverter.setIndentOutput(true);
		return jsonConverter;
	}
}
