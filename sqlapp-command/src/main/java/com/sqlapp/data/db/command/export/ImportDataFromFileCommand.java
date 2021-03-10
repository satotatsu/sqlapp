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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.export.TableFileReader.TableFilesPair;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ColumnCollection;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.CombinedRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.CsvRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.ExcelRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.data.schemas.rowiterator.XmlRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.YamlRowIteratorHandler;
import com.sqlapp.exceptions.InvalidValueException;
import com.sqlapp.jdbc.sql.JdbcBatchUpdateHandler;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

public class ImportDataFromFileCommand extends AbstractExportCommand{
	
	private boolean useTableNameDirectory=false;
	
	private long queryCommitInterval=Long.MAX_VALUE;
	
	/**file directory*/
	private File fileDirectory=null;
	/**
	 * data file
	 */
	private File file=null;
	/**SQL Type*/
	private SqlType sqlType=SqlType.MERGE_ROW;
	/**file filter*/
	private Predicate<File> fileFilter=f->true;

	private String placeholderPrefix="${";

	private String placeholderSuffix="}";

	private boolean placeholders=false;

	private int csvSkipHeaderRowsSize=1;
	
	private RowValueConverter rowValueConverter;

	public ImportDataFromFileCommand(){
	}
	
	@Override
	protected void doRun() {
		Connection connection=null;
		try{
			connection=this.getConnection();
			final Dialect dialect=this.getDialect();
			SchemaReader schemaReader=null;
			try {
				schemaReader = getSchemaReader(connection, dialect);
			} catch (final SQLException e) {
				this.getExceptionHandler().handle(e);
			}
			final Set<String> schemaNames=CommonUtils.lowerSet();
			if (isUseSchemaNameDirectory()){
				final File[] directories=getDirectory().listFiles(c->c.isDirectory());
				if (directories!=null) {
					for(final File directory:directories){
						final String name=directory.getName();
						schemaNames.add(name);
					}
				}
			}
			final TableFileReader tableFileReader=createTableFileReader();
			final Map<String, Schema> schemaMap;
			if (isUseSchemaNameDirectory()){
				schemaMap=this.getSchemas(connection, dialect, schemaReader, (s)->schemaNames.contains(s.getName()));
			} else{
				schemaMap=this.getSchemas(connection, dialect, schemaReader, s->true);
			}
			final Catalog catalog=new Catalog();
			catalog.setDialect(dialect);
			schemaMap.forEach((k,v)->{
				catalog.getSchemas().add(v);
			});
			List<TableFilesPair> tfs=tableFileReader.getTableFilePairs(catalog);
			try {
				tableFileReader.setFiles(tfs);
			} catch (EncryptedDocumentException | InvalidFormatException | IOException | XMLStreamException e) {
				this.getExceptionHandler().handle(e);
			}
			if (this.getSqlType().getTableComparator()!=null){
				tfs=SchemaUtils.getNewSortedTableList(tfs, this.getSqlType().getTableComparator(), tf->tf.getTable());
			}
			connection.setAutoCommit(false);
			int commitCount=0;
			for(final TableFilesPair tf:tfs){
				this.println("target="+tf);
				if (this.getTableOptions().getCommitPerTable().test(tf.getTable())){
					try{
						executeImport(connection, dialect, tf.getTable(), tf.getFiles());
						connection.commit();
						commitCount++;
					} catch (final SQLException e) {
						rollback(connection);
						this.getExceptionHandler().handle(e);
					}
				} else{
					executeImport(connection, dialect, tf.getTable(), tf.getFiles());
				}
			}
			if (commitCount==0){
				connection.commit();
			}
		} catch (final RuntimeException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		} catch (final SQLException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		}
	}

	private TableFileReader createTableFileReader(){
		final TableFileReader tableFileReader=new TableFileReader();
		tableFileReader.setContext(this.getContext());
		tableFileReader.setCsvEncoding(this.getCsvEncoding());
		tableFileReader.setDirectory(this.getDirectory());
		tableFileReader.setFileDirectory(this.getFileDirectory());
		tableFileReader.setFileFilter(this.getFileFilter());
		tableFileReader.setJsonConverter(this.getJsonConverter());
		tableFileReader.setPlaceholderPrefix(this.getPlaceholderPrefix());
		tableFileReader.setPlaceholders(this.isPlaceholders());
		tableFileReader.setPlaceholderSuffix(this.getPlaceholderSuffix());
		tableFileReader.setUseSchemaNameDirectory(this.isUseSchemaNameDirectory());
		tableFileReader.setUseTableNameDirectory(this.isUseTableNameDirectory());
		return tableFileReader;
	}
	
	private void rollback(final Connection connection){
		if (connection==null){
			return;
		}
		try {
			connection.rollback();
		} catch (final SQLException e) {
		}
	}

	protected void executeImport(final Connection connection, final Dialect dialect, final Table table, final List<File> files) throws SQLException{
		try {
			if (this.getSqlType().supportRows()){
				applyFromFileByRow(connection, dialect, table, files);
			} else{
				applyFromFileByTable(connection, dialect, table, files);
			}
		} catch (final EncryptedDocumentException e) {
			this.getExceptionHandler().handle(e);
		} catch (final InvalidFormatException e) {
			this.getExceptionHandler().handle(e);
		} catch (final XMLStreamException e) {
			this.getExceptionHandler().handle(e);
		} catch (final IOException e) {
			this.getExceptionHandler().handle(e);
		}
	}
	
	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table, final List<File> files) throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException, SQLException{
		final SqlFactoryRegistry sqlFactoryRegistry=dialect.getSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(this.getTableOptions());
		final SqlFactory<Row> factory=sqlFactoryRegistry.getSqlFactory(new Row(), this.getSqlType());
		long queryCount=0;
		final List<File> targets=CommonUtils.list();
		if (!CommonUtils.isEmpty(files)) {
			for(final File file:files){
				if (file.isDirectory()){
					for(final File children:file.listFiles()){
						targets.add(children);
					}
				} else{
					targets.add(file);
				}
			}
			readFiles(table, targets);
		}
		final SqlConverter sqlConverter=getSqlConverter();
		final List<Row> batchRows=CommonUtils.list();
		try {
			for(final Row row:table.getRows()){
				batchRows.add(row);
				if (batchRows.size()>this.getTableOptions().getDmlBatchSize().apply(table)){
					final List<SqlOperation> operations=factory.createSql(batchRows);
					final ParametersContext context=new ParametersContext();
					context.putAll(this.getContext());
					context.putAll(convert(row, table.getColumns()));
					for(final SqlOperation operation:operations){
						final SqlNode sqlNode=sqlConverter.parseSql(context, operation.getSqlText());
						final JdbcHandler jdbcHandler=new JdbcHandler(sqlNode);
						jdbcHandler.execute(connection, context);
						queryCount=commit(connection, queryCount);
					}
					batchRows.clear();
				}
			}
		} finally {
			table.setRowIteratorHandler(null);
		}
		if (batchRows.size()>0){
			final List<SqlOperation> operations=factory.createSql(batchRows);
			final ParametersContext context=new ParametersContext();
			context.putAll(this.getContext());
			for(final SqlOperation operation:operations){
				final SqlNode sqlNode=sqlConverter.parseSql(context, operation.getSqlText());
				final JdbcHandler jdbcHandler=new JdbcHandler(sqlNode);
				jdbcHandler.execute(connection, context);
				commit(connection, queryCount);
			}
			batchRows.clear();
		}
	}
	
	protected SqlConverter getSqlConverter(){
		final SqlConverter sqlConverter=new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}


	private long commit(final Connection connection, final long queryCount) throws SQLException{
		if (queryCount>=this.getQueryCommitInterval()){
			connection.commit();
			return 0;
		}
		return queryCount+1;
	}

	protected void applyFromFileByTable(final Connection connection, final Dialect dialect, final Table table, final List<File> files) throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException, SQLException{
		final SqlFactoryRegistry sqlFactoryRegistry=dialect.getSqlFactoryRegistry();
		final SqlFactory<Table> factory=sqlFactoryRegistry.getSqlFactory(table, this.getSqlType());
		final List<SqlOperation> operations=factory.createSql(table);
		final SqlConverter sqlConverter=getSqlConverter();
		final List<JdbcBatchUpdateHandler> handlers=operations.stream().map(c->{
			final ParametersContext context=new ParametersContext();
			context.putAll(this.getContext());
			final SqlNode sqlNode=sqlConverter.parseSql(context, c.getSqlText());
			final JdbcBatchUpdateHandler jdbcHandler=new JdbcBatchUpdateHandler(sqlNode);
			return jdbcHandler;
		}).collect(Collectors.toList());
		long queryCount=0;
		final List<File> targets=CommonUtils.list();
		if (!CommonUtils.isEmpty(files)) {
			for(final File file:files){
				if (file.isDirectory()){
					for(final File children:file.listFiles()){
						targets.add(children);
					}
				} else{
					targets.add(file);
				}
			}
			readFiles(table, targets);
		}
		final List<ParametersContext> batchRows=CommonUtils.list();
		try {
			for(final Row row:table.getRows()){
				final ParametersContext context=new ParametersContext();
				context.putAll(this.getContext());
				context.putAll(convert(row, table.getColumns()));
				batchRows.add(context);
				if (batchRows.size()>this.getTableOptions().getDmlBatchSize().apply(table)){
					for(final JdbcBatchUpdateHandler jdbcHandler:handlers){
						jdbcHandler.execute(connection, batchRows);
						queryCount=commit(connection, queryCount);
					}
					batchRows.clear();
				}
			}
		} finally {
			table.setRowIteratorHandler(null);
		}
		if (batchRows.size()>0){
			for(final JdbcBatchUpdateHandler jdbcHandler:handlers){
				jdbcHandler.execute(connection, batchRows);
				commit(connection, queryCount);
			}
			batchRows.clear();
		}
	}
	
	private Map<String,Object> convert(final Row row, final ColumnCollection columns){
		final Map<String,Object> map=row.toMap();
		final Map<String,Object> ret=CommonUtils.map(map.size());
		final SqlConverter sqlConverter=getSqlConverter();
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		for(final Column column:columns){
			final Object originalValue=row.get(column);
			Object val;
			try {
				val = sqlConverter.getExpressionConverter().convert(originalValue, context);
			} catch (final IOException e) {
				throw new InvalidValueException(row.getDataSourceInfo(), row.getDataSourceDetailInfo(), column.getName(), originalValue, e);
			}
			ret.put(column.getName(), val);
		}
		return ret;
	}

	private RowValueConverter createRowValueConverter(){
		final SqlConverter sqlConverter=getSqlConverter();
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		return (r, c, v)->{
//			if (this.getSqlType().supportRows()){
//				return v;
//			}
			Object originalVal;
			if (this.createRowValueConverter()!=null) {
				originalVal=this.createRowValueConverter().apply(r, c, v);
			} else {
				originalVal=v;
			}
			Object val;
			try {
				val = sqlConverter.getExpressionConverter().convert(originalVal, context);
			} catch (final IOException e) {
				throw new InvalidValueException(r, c, v, e);
			}
			return val;
		};
	}

	private void readFiles(final Table table, final List<File> files) throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException{
		final List<RowIteratorHandler> handlers=files.stream().map(file->{
			final WorkbookFileType workbookFileType=WorkbookFileType.parse(file);
			if (workbookFileType.isTextFile()){
				if (workbookFileType.isCsv()){
					return new CsvRowIteratorHandler(file, getCsvEncoding(), createRowValueConverter());
				} else if (workbookFileType.isXml()){
					return new XmlRowIteratorHandler(file, createRowValueConverter());
				} else if (workbookFileType.isYaml()){
					return new YamlRowIteratorHandler(file, this.getYamlConverter(), createRowValueConverter());
				} else {
					return new JsonRowIteratorHandler(file, this.getJsonConverter(), createRowValueConverter());
				}
			} else{
				return new ExcelRowIteratorHandler(file, createRowValueConverter());
			}
		}).collect(Collectors.toList());
		table.setRowIteratorHandler(new CombinedRowIteratorHandler(handlers));
	}

	protected void readFileAsXml(final Table table, final File file, final WorkbookFileType workbookFileType) throws XMLStreamException, FileNotFoundException{
		final XmlReaderOptions options=new XmlReaderOptions();
		options.setRowValueConverter(createRowValueConverter());
		table.loadXml(file, options);
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
	 * @return the queryCommitInterval
	 */
	public long getQueryCommitInterval() {
		return queryCommitInterval;
	}

	/**
	 * @param queryCommitInterval the queryCommitInterval to set
	 */
	public void setQueryCommitInterval(final long queryCommitInterval) {
		this.queryCommitInterval = queryCommitInterval;
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

	public File getFile() {
		return file;
	}

	public void setFile(final File file) {
		this.file = file;
	}

	/**
	 * @return the sqlType
	 */
	public SqlType getSqlType() {
		return sqlType;
	}

	/**
	 * @param sqlType the sqlType to set
	 */
	public void setSqlType(final SqlType sqlType) {
		this.sqlType = sqlType;
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

	public int getCsvSkipHeaderRowsSize() {
		return csvSkipHeaderRowsSize;
	}

	public void setCsvSkipHeaderRowsSize(final int csvSkipHeaderRowsSize) {
		this.csvSkipHeaderRowsSize = csvSkipHeaderRowsSize;
	}

	public void setRowValueConverter(final RowValueConverter rowValueConverter) {
		this.rowValueConverter = rowValueConverter;
	}

	public RowValueConverter getRowValueConverter() {
		return rowValueConverter;
	}

}
