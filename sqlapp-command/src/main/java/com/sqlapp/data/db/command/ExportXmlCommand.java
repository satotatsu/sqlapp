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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.MetadataReader;
import com.sqlapp.data.db.metadata.MetadataReaderUtils;
import com.sqlapp.data.db.metadata.ObjectNameReaderPredicate;
import com.sqlapp.data.db.metadata.ReadDbObjectPredicate;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.RowIteratorHandlerProperty;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.TableNameRowCollectionFilter;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.StaxWriter;
import com.sqlapp.util.StringUtils;

/**
 * Export XMLコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public class ExportXmlCommand extends AbstractSchemaDataSourceCommand {
	/**
	 * catalogs,catalog,schemas,schema,tables...
	 */
	private String target = "catalog";
	/**
	 * Output Path
	 */
	private File outputPath;
	/**
	 * 行のダンプ
	 */
	private boolean dumpRows = true;
	/**
	 * 行のダンプを行うテーブル
	 */
	private String[] includeRowDumpTables = null;
	/**
	 * 行のダンプから除くテーブル
	 */
	private String[] excludeRowDumpTables = null;
	/**
	 * ダンプに含めるスキーマ
	 */
	private String[] includeSchemas = null;
	/**
	 * ダンプから除くスキーマ
	 */
	private String[] excludeSchemas = null;
	/**
	 * ダンプに含めるオブジェクト
	 */
	private String[] includeObjects = null;
	/**
	 * ダンプから除くオブジェクト
	 */
	private String[] excludeObjects = null;
	/**
	 * Output FileName
	 */
	private String outputFileName;

	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	private boolean onlyCurrentCatalog = true;
	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	private boolean onlyCurrentSchema = false;
	/**
	 * Before output Converter
	 */
	private Consumer<DbObject<?>> converter=(c)->{};
	private Options options=null;
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@SuppressWarnings("rawtypes")
	@Override
	protected void doRun() {
		Connection connection=null;
		final Dialect dialect;
		try{
			connection=this.getConnection();
			dialect=this.getDialect(connection);
		} finally {
			releaseConnection(connection);
		}
		final MetadataReader reader = getMetadataReader(dialect);
		RowIteratorHandler rowIteratorHandler = null;
		if (isDumpRows()) {
			rowIteratorHandler = getRowIteratorHandler();
		}
		final ReadDbObjectPredicate readerFilter = getMetadataReaderFilter();
		reader.setReadDbObjectPredicate(readerFilter);
		@SuppressWarnings({ "unchecked" })
		List<DbObject> list = readDbMetadataReader(reader);
		for (final DbObject object : list) {
			if (object instanceof RowIteratorHandlerProperty) {
				((RowIteratorHandlerProperty) object)
						.setRowIteratorHandler(rowIteratorHandler);
			}
		}
		list = getConvertHandler().handle(list);
		for (final DbObject<?> object : list) {
			object.applyAll(converter);
		}
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		Writer writer=null;
		final String rootElementName = SchemaUtils.getPluralName(this.getTarget());
		try {
			FileUtils.createParentDirectory(getOutputFileFullPath());
			fos = new FileOutputStream(getOutputFileFullPath());
			bos = new BufferedOutputStream(fos);
			writer = new OutputStreamWriter(bos, "UTF-8");
			final StaxWriter staxWriter = new StaxWriter(writer);
			if (this.getTarget().endsWith("s")) {
				staxWriter.writeStartElement(rootElementName);
				staxWriter.addIndentLevel(1);
			}
			SchemaUtils.writeAllXml(list, staxWriter);
			if (this.getTarget().endsWith("s")) {
				staxWriter.addIndentLevel(-1);
				staxWriter.newLine();
				staxWriter.indent();
				staxWriter.writeEndElement();
			}
		} catch (final FileNotFoundException e) {
			this.getExceptionHandler().handle(e);
		} catch (final XMLStreamException e) {
			this.getExceptionHandler().handle(e);
		} catch (final UnsupportedEncodingException e) {
			this.getExceptionHandler().handle(e);
		} finally {
			FileUtils.close(writer);
			FileUtils.close(bos);
			FileUtils.close(fos);
		}
	}

	@SuppressWarnings("rawtypes")
	protected MetadataReader getMetadataReader(final Dialect dialect) {
		final MetadataReader reader = MetadataReaderUtils.getMetadataReader(
				dialect, this.getTarget());
		final Connection connection = this.getConnection();
		final String catalogName = getCurrentCatalogName(connection, dialect);
		final String schemaName = getCurrentSchemaName(connection, dialect);
		if (this.isOnlyCurrentCatalog()) {
			SimpleBeanUtils.setValue(reader, "catalogName", catalogName);
		}
		if (this.isOnlyCurrentSchema()) {
			SimpleBeanUtils.setValue(reader, "schemaName", schemaName);
		}
		return reader;
	}

	private <T extends DbObject<? super T>> List<T> readDbMetadataReader(
			final MetadataReader<T, ?> dbMetadataReader) {
		return dbMetadataReader.getAllFull(this.getConnection());
	}

	protected RowIteratorHandler getRowIteratorHandler() {
		final JdbcDynamicRowIteratorHandler rowIteratorHandler = new JdbcDynamicRowIteratorHandler();
		rowIteratorHandler.setDataSource(this.getDataSource());
		rowIteratorHandler.setOptions(this.getOptions());
		final TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
		filter.setIncludes(this.getIncludeRowDumpTables());
		filter.setExcludes(this.getExcludeRowDumpTables());
		rowIteratorHandler.setFilter(filter);
		return rowIteratorHandler;
	}

	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(
				this.getIncludeSchemas(), this.getExcludeSchemas(),
				this.getIncludeObjects(), this.getExcludeObjects());
		return readerFilter;
	}

	/**
	 * @return the target
	 */
	public String getTarget() {
		return target;
	}

	/**
	 * @param target
	 *            the target to set
	 */
	public void setTarget(final String target) {
		this.target = target;
	}

	/**
	 * @return the dumpRows
	 */
	public boolean isDumpRows() {
		return dumpRows;
	}

	/**
	 * @param dumpRows
	 *            the dumpRows to set
	 */
	public void setDumpRows(final boolean dumpRows) {
		this.dumpRows = dumpRows;
	}

	/**
	 * @return the includeRowDumpTables
	 */
	public String[] getIncludeRowDumpTables() {
		return includeRowDumpTables;
	}

	/**
	 * @param includeRowDumpTables
	 *            the includeRowDumpTables to set
	 */
	public void setIncludeRowDumpTables(final String... includeRowDumpTables) {
		this.includeRowDumpTables = includeRowDumpTables;
	}

	/**
	 * @return the excludeRowDumpTables
	 */
	public String[] getExcludeRowDumpTables() {
		return excludeRowDumpTables;
	}

	/**
	 * @param excludeRowDumpTables
	 *            the excludeRowDumpTables to set
	 */
	public void setExcludeRowDumpTables(final String... excludeRowDumpTables) {
		this.excludeRowDumpTables = excludeRowDumpTables;
	}

	/**
	 * @return the includeSchemas
	 */
	public String[] getIncludeSchemas() {
		return includeSchemas;
	}

	/**
	 * @param includeSchemas
	 *            the includeSchemas to set
	 */
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = includeSchemas;
	}

	/**
	 * @return the excludeSchemas
	 */
	public String[] getExcludeSchemas() {
		return excludeSchemas;
	}

	/**
	 * @param excludeSchemas
	 *            the excludeSchemas to set
	 */
	public void setExcludeSchemas(final String... excludeSchemas) {
		this.excludeSchemas = excludeSchemas;
	}

	/**
	 * @return the includeObjects
	 */
	public String[] getIncludeObjects() {
		return includeObjects;
	}

	/**
	 * @param includeObjects
	 *            the includeObjects to set
	 */
	public void setIncludeObjects(final String... includeObjects) {
		this.includeObjects = includeObjects;
	}

	/**
	 * @return the excludeObjects
	 */
	public String[] getExcludeObjects() {
		return excludeObjects;
	}

	/**
	 * @param excludeObjects
	 *            the excludeObjects to set
	 */
	public void setExcludeObjects(final String... excludeObjects) {
		this.excludeObjects = excludeObjects;
	}

	/**
	 * @return the outputFileName
	 */
	public String getOutputFileName() {
		if (this.outputFileName==null){
			this.outputFileName = StringUtils.capitalize(target) + ".xml";
		}
		return outputFileName;
	}

	/**
	 * @param outputFileName
	 *            the outputFileName to set
	 */
	public void setOutputFileName(final String outputFileName) {
		this.outputFileName = outputFileName;
	}

	/**
	 * @return the outputPath
	 */
	public File getOutputPath() {
		return outputPath;
	}

	/**
	 * @param outputPath
	 *            the outputPath to set
	 */
	public void setOutputPath(final File outputPath) {
		this.outputPath = outputPath;
	}

	public String getOutputFileFullPath() {
		return FileUtils.combinePath(getOutputPath(),
				CommonUtils.coalesce(this.getOutputFileName(), "dump.xml"));
	}

	/**
	 * @return the onlyCurrentCatalog
	 */
	public boolean isOnlyCurrentCatalog() {
		return onlyCurrentCatalog;
	}

	/**
	 * @param onlyCurrentCatalog
	 *            the onlyCurrentCatalog to set
	 */
	public void setOnlyCurrentCatalog(final boolean onlyCurrentCatalog) {
		this.onlyCurrentCatalog = onlyCurrentCatalog;
	}

	/**
	 * @return the onlyCurrentSchema
	 */
	public boolean isOnlyCurrentSchema() {
		return onlyCurrentSchema;
	}

	/**
	 * @param onlyCurrentSchema
	 *            the onlyCurrentSchema to set
	 */
	public void setOnlyCurrentSchema(final boolean onlyCurrentSchema) {
		this.onlyCurrentSchema = onlyCurrentSchema;
	}

	/**
	 * @return the converter
	 */
	public Consumer<DbObject<?>> getConverter() {
		return converter;
	}

	/**
	 * @param converter the converter to set
	 */
	public void setConverter(final Consumer<DbObject<?>> converter) {
		this.converter = converter;
	}

	public Options getOptions() {
		return options;
	}

	public void setOptions(final Options options) {
		this.options = options;
	}

}
