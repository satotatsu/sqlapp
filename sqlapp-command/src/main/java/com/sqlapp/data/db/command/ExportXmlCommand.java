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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

import com.sqlapp.data.db.command.properties.ObjectTargetProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentCatalogProperty;
import com.sqlapp.data.db.command.properties.OnlyCurrentSchemaProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.SchemaOptionProperty;
import com.sqlapp.data.db.command.properties.SchemaTargetProperty;
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

import lombok.Getter;
import lombok.Setter;

/**
 * Export XMLコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public class ExportXmlCommand extends AbstractSchemaDataSourceCommand
		implements SchemaOptionProperty, OnlyCurrentCatalogProperty, OnlyCurrentSchemaProperty, SchemaTargetProperty,
		ObjectTargetProperty, OutputDirectoryProperty {
	/**
	 * catalogs,catalog,schemas,schema,tables...
	 */
	private String target = "catalog";
	/**
	 * Output Path
	 */
	private File outputDirectory;
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
	private Consumer<DbObject<?>> converter = (c) -> {
	};
	private Options schemaOptions = new Options();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected void doRun() {
		List<DbObject>[] list = new List[1];
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final MetadataReader reader = getMetadataReader(connection, dialect);
			final ReadDbObjectPredicate readerFilter = getMetadataReaderFilter();
			reader.setReadDbObjectPredicate(readerFilter);
			list[0] = readDbMetadataReader(connection, reader);
			if (isDumpRows()) {
				final RowIteratorHandler rowIteratorHandler = getRowIteratorHandler();
				for (final DbObject object : list[0]) {
					if (object instanceof RowIteratorHandlerProperty) {
						((RowIteratorHandlerProperty) object).setRowIteratorHandler(rowIteratorHandler);
					}
				}
			}
			list[0] = getConvertHandler().handle(list[0]);
			for (final DbObject<?> object : list[0]) {
				object.applyAll(converter);
			}
		});
		final String rootElementName = SchemaUtils.getPluralName(this.getTarget());
		FileUtils.createParentDirectory(getOutputFileFullPath());
		execute(() -> {
			try (FileOutputStream fos = new FileOutputStream(getOutputFileFullPath());
					BufferedOutputStream bos = new BufferedOutputStream(fos);
					Writer writer = new OutputStreamWriter(bos, "UTF-8");) {
				final StaxWriter staxWriter = new StaxWriter(writer);
				if (this.getTarget().endsWith("s")) {
					staxWriter.writeStartElement(rootElementName);
					staxWriter.addIndentLevel(1);
				}
				SchemaUtils.writeAllXml(list[0], staxWriter);
				if (this.getTarget().endsWith("s")) {
					staxWriter.addIndentLevel(-1);
					staxWriter.newLine();
					staxWriter.indent();
					staxWriter.writeEndElement();
				}
			}
		});
	}

	@SuppressWarnings("rawtypes")
	protected MetadataReader getMetadataReader(Connection connection, final Dialect dialect) throws SQLException {
		final MetadataReader reader = MetadataReaderUtils.getMetadataReader(dialect, this.getTarget());
		final String catalogName = getCurrentCatalogName(connection);
		final String schemaName = getCurrentSchemaName(connection);
		if (this.isOnlyCurrentCatalog()) {
			SimpleBeanUtils.setValue(reader, "catalogName", catalogName);
		}
		if (this.isOnlyCurrentSchema()) {
			SimpleBeanUtils.setValue(reader, "schemaName", schemaName);
		}
		return reader;
	}

	private <T extends DbObject<? super T>> List<T> readDbMetadataReader(Connection connection,
			final MetadataReader<T, ?> dbMetadataReader) {
		return dbMetadataReader.getAllFull(connection);
	}

	protected RowIteratorHandler getRowIteratorHandler() {
		final JdbcDynamicRowIteratorHandler rowIteratorHandler = new JdbcDynamicRowIteratorHandler();
		rowIteratorHandler.setDataSource(this.getDataSource());
		rowIteratorHandler.setOptions(this.getSchemaOptions());
		final TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
		filter.setIncludes(this.getIncludeRowDumpTables());
		filter.setExcludes(this.getExcludeRowDumpTables());
		rowIteratorHandler.setFilter(filter);
		return rowIteratorHandler;
	}

	protected ReadDbObjectPredicate getMetadataReaderFilter() {
		final ReadDbObjectPredicate readerFilter = new ObjectNameReaderPredicate(this.getIncludeSchemas(),
				this.getExcludeSchemas(), this.getIncludeObjects(), this.getExcludeObjects());
		return readerFilter;
	}

	/**
	 * @param includeRowDumpTables the includeRowDumpTables to set
	 */
	public void setIncludeRowDumpTables(final String... includeRowDumpTables) {
		this.includeRowDumpTables = includeRowDumpTables;
	}

	/**
	 * @param excludeRowDumpTables the excludeRowDumpTables to set
	 */
	public void setExcludeRowDumpTables(final String... excludeRowDumpTables) {
		this.excludeRowDumpTables = excludeRowDumpTables;
	}

	/**
	 * @param includeSchemas the includeSchemas to set
	 */
	public void setIncludeSchemas(final String... includeSchemas) {
		this.includeSchemas = includeSchemas;
	}

	/**
	 * @param excludeSchemas the excludeSchemas to set
	 */
	public void setExcludeSchemas(final String... excludeSchemas) {
		this.excludeSchemas = excludeSchemas;
	}

	/**
	 * @param includeObjects the includeObjects to set
	 */
	public void setIncludeObjects(final String... includeObjects) {
		this.includeObjects = includeObjects;
	}

	/**
	 * @param excludeObjects the excludeObjects to set
	 */
	public void setExcludeObjects(final String... excludeObjects) {
		this.excludeObjects = excludeObjects;
	}

	/**
	 * @return the outputFileName
	 */
	public String getOutputFileName() {
		if (this.outputFileName == null) {
			this.outputFileName = StringUtils.capitalize(target) + ".xml";
		}
		return outputFileName;
	}

	public String getOutputFileFullPath() {
		return FileUtils.combinePath(getOutputDirectory(), CommonUtils.coalesce(this.getOutputFileName(), "dump.xml"));
	}
}
