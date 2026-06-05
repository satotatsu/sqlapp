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
import com.sqlapp.data.db.command.properties.CommitPerTableProperty;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.FileDirectoryProperty;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.data.db.command.properties.PropertyUtils;
import com.sqlapp.data.db.command.properties.QueryCommitIntervalProperty;
import com.sqlapp.data.db.command.properties.SqlTypeProperty;
import com.sqlapp.data.db.command.properties.TableOptionProperty;
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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.XmlReaderOptions;
import com.sqlapp.data.schemas.function.RowValueConverter;
import com.sqlapp.data.schemas.rowiterator.CombinedRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.CsvRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.ExcelRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.JsonRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.TomlRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.data.schemas.rowiterator.XmlRowIteratorHandler;
import com.sqlapp.data.schemas.rowiterator.YamlRowIteratorHandler;
import com.sqlapp.exceptions.InvalidValueException;
import com.sqlapp.jdbc.sql.GeneratedKeyInfo;
import com.sqlapp.jdbc.sql.JdbcBatchIterateHander;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportDataFromFileCommand extends AbstractExportCommand
		implements PlaceholderProperty, TableOptionProperty, SqlTypeProperty, FileDirectoryProperty, FilesProperty,
		QueryCommitIntervalProperty, DirectoryProperty, CommitPerTableProperty {

	private long queryCommitInterval = Long.MAX_VALUE;
	/**
	 * Output Directory
	 */
	private File directory = new File(".");
	/** file directory */
	private File fileDirectory = null;
	/**
	 * data file
	 */
	private File[] files = null;
	/** SQL Type */
	private SqlType sqlType = SqlType.MERGE_ROW;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;

	private String placeholderPrefix = "${";

	private String placeholderSuffix = "}";

	private boolean placeholders = false;

	private int csvSkipHeaderRowsSize = 1;

	private int excelSkipHeaderRowsSize = 1;

	private RowValueConverter rowValueConverter;

	public ImportDataFromFileCommand() {
		this.setDmlBatchSize(500);
	}

	@Override
	protected void doRun() {
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final SchemaReader schemaReader = getSchemaReader(connection, dialect);
			final Set<String> schemaNames = CommonUtils.lowerSet();
			if (isUseSchemaNameDirectory()) {
				final File[] directories = getDirectory().listFiles(c -> c.isDirectory());
				if (directories != null) {
					for (final File directory : directories) {
						final String name = directory.getName();
						schemaNames.add(name);
					}
				}
			}
			final TableFileReader tableFileReader = createTableFileReader();
			final Map<String, Schema> schemaMap;
			if (isUseSchemaNameDirectory()) {
				schemaMap = this.getSchemas(connection, dialect, schemaReader,
						(s) -> schemaNames.contains(s.getName()));
			} else {
				schemaMap = this.getSchemas(connection, dialect, schemaReader, s -> true);
			}
			final Catalog catalog = new Catalog();
			catalog.setDialect(dialect);
			schemaMap.forEach((k, v) -> {
				catalog.getSchemas().add(v);
			});
			final List<TableFilesPair> tfs = tableFileReader.getTableFilesPairs(catalog);
			tableFileReader.setFiles(tfs);
			if (this.getSqlType().getTableOrder() != null) {
				List<TableFilesPair> sorted = this.getSqlType().getTableOrder().sort(tfs, tf -> tf.getTable());
				tfs.clear();
				tfs.addAll(sorted);
			}
			connection.setAutoCommit(false);
			int commitCount = 0;
			for (final TableFilesPair tf : tfs) {
				this.info("target=" + tf);
				if (this.getTableOptions().getCommitPerTable().test(tf.getTable())) {
					executeImport(connection, dialect, tf.getTable(), tf.getFiles());
					commit(connection);
					commitCount++;
				} else {
					executeImport(connection, dialect, tf.getTable(), tf.getFiles());
				}
			}
			if (commitCount == 0) {
				commit(connection);
			}
		});
	}

	private TableFileReader createTableFileReader() {
		final TableFileReader tableFileReader = new TableFileReader();
		tableFileReader.setContext(this.getContext());
		tableFileReader.setCsvEncoding(this.getCsvEncoding());
		tableFileReader.setDirectory(this.getDirectory());
		tableFileReader.setFileDirectory(this.getFileDirectory());
		tableFileReader.setFileFilter(this.getFileFilter());
		tableFileReader.setFiles(this.getFiles());
		tableFileReader.setJsonConverter(this.getJsonConverter());
		tableFileReader.setPlaceholderPrefix(this.getPlaceholderPrefix());
		tableFileReader.setPlaceholders(this.isPlaceholders());
		tableFileReader.setPlaceholderSuffix(this.getPlaceholderSuffix());
		tableFileReader.setUseSchemaNameDirectory(this.isUseSchemaNameDirectory());
		return tableFileReader;
	}

	protected void executeImport(final Connection connection, final Dialect dialect, final Table table,
			final List<File> files)
			throws SQLException, EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException {
		if (this.getSqlType().supportRows()) {
			applyFromFileByRow(connection, dialect, table, files);
		} else {
			applyFromFileByTable(connection, dialect, table, files);
		}
	}

	protected void applyFromFileByRow(final Connection connection, final Dialect dialect, final Table table,
			final List<File> files)
			throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException, SQLException {
		final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		sqlFactoryRegistry.getOption().setTableOptions(this.getTableOptions());
		final SqlFactory<Row> factory = sqlFactoryRegistry.getSqlFactory(new Row(), this.getSqlType());
		long queryCount = 0;
		final List<File> targets = CommonUtils.list();
		if (!CommonUtils.isEmpty(files)) {
			for (final File file : files) {
				if (file.isDirectory()) {
					final File[] listFiles = file.listFiles();
					if (listFiles != null) {
						for (final File children : listFiles) {
							targets.add(children);
						}
					}
				} else {
					targets.add(file);
				}
			}
			readFiles(table, targets);
		}
		final SqlConverter sqlConverter = getSqlConverter();
		final int batchSize = this.getTableOptions().getDmlBatchSize().apply(table);
		final List<Row> batchRows = CommonUtils.list(batchSize);
		try {
			for (final Row row : table.getRows()) {
				batchRows.add(row);
				if (batchRows.size() >= batchSize) {
					final List<SqlOperation> operations = factory.createSql(batchRows);
					final ParametersContext context = new ParametersContext();
					context.putAll(this.getContext());
					context.putAll(convert(sqlConverter, row, table.getColumns()));
					for (final SqlOperation operation : operations) {
						final SqlNode sqlNode = sqlConverter.parseSql(context, operation.getSqlText());
						final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
						jdbcHandler.execute(connection, context);
						queryCount = commit(connection, queryCount);
					}
					batchRows.clear();
				}
			}
		} finally {
			table.setRowIteratorHandler(null);
		}
		if (batchRows.size() > 0) {
			final List<SqlOperation> operations = factory.createSql(batchRows);
			final ParametersContext context = new ParametersContext();
			context.putAll(this.getContext());
			for (final SqlOperation operation : operations) {
				final SqlNode sqlNode = sqlConverter.parseSql(context, operation.getSqlText());
				final JdbcHandler jdbcHandler = new JdbcHandler(sqlNode);
				jdbcHandler.execute(connection, context);
				commit(connection);
			}
			batchRows.clear();
		}
	}

	protected SqlConverter getSqlConverter() {
		final SqlConverter sqlConverter = new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}

	private long commit(final Connection connection, final long queryCount) throws SQLException {
		if ((queryCount + 1) >= this.getQueryCommitInterval()) {
			commit(connection);
			return 0;
		}
		return queryCount + 1;
	}

	protected void applyFromFileByTable(final Connection connection, final Dialect dialect, final Table table,
			final List<File> files)
			throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException, SQLException {
		final SqlFactoryRegistry sqlFactoryRegistry = dialect.createSqlFactoryRegistry();
		final SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, this.getSqlType());
		final List<SqlOperation> operations = factory.createSql(table);
		final SqlConverter sqlConverter = getSqlConverter();
		final List<SqlNode> sqlNodes = operations.stream().map(c -> {
			final ParametersContext context = new ParametersContext();
			context.putAll(this.getContext());
			final SqlNode sqlNode = sqlConverter.parseSql(context, c.getSqlText());
			return sqlNode;
		}).collect(Collectors.toList());
		final List<File> targets = CommonUtils.list();
		if (!CommonUtils.isEmpty(files)) {
			for (final File file : files) {
				if (file.isDirectory()) {
					final File[] listFiles = file.listFiles();
					if (listFiles != null) {
						for (final File children : listFiles) {
							targets.add(children);
						}
					}
				} else {
					targets.add(file);
				}
			}
			readFiles(table, targets);
		}
		try {
			final JdbcBatchIterateHander handler = new JdbcBatchIterateHander(sqlNodes,
					this.getTableOptions().getDmlBatchSize().apply(table), this.getQueryCommitInterval());
			handler.setValueConverter(r -> {
				final ParametersContext context = new ParametersContext();
				context.putAll(this.getContext());
				context.putAll(convert(sqlConverter, (Row) r, table.getColumns()));
				return context;
			});
			handler.setBatchUpdateResultHandler(result -> {
				// INSERTで生成されたキーを反映する
				final int max = result.getGeneratedKeys().size();
				for (int i = 0; i < max; i++) {
					final GeneratedKeyInfo gk = result.getGeneratedKeys().get(i);
					final Row row = (Row) result.getValues().get(i).value();
					Column column = table.getColumns().get(gk.getColumnName());
					row.put(column, gk.getValue());
				}
			});
			handler.execute(connection, table.getRows());
		} finally {
			table.setRowIteratorHandler(null);
		}
	}

	private Map<String, Object> convert(final SqlConverter sqlConverter, final Row row,
			final ColumnCollection columns) {
		final Map<String, Object> map = row.toMap();
		final Map<String, Object> ret = CommonUtils.map(map.size());
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		for (final Column column : columns) {
			final Object originalValue = row.get(column);
			Object val;
			try {
				val = sqlConverter.getExpressionConverter().convert(originalValue, context);
			} catch (final IOException e) {
				throw new InvalidValueException(row.getDataSourceInfo(), row.getDataSourceDetailInfo(),
						column.getName(), originalValue, e);
			}
			ret.put(column.getName(), val);
		}
		return ret;
	}

	private RowValueConverter createRowValueConverter() {
		final SqlConverter sqlConverter = getSqlConverter();
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		return (r, c, v) -> {
			if (this.getSqlType().supportRows()) {
				return v;
			}
			Object originalVal;
			if (this.getRowValueConverter() != null) {
				originalVal = this.getRowValueConverter().apply(r, c, v);
			} else {
				originalVal = v;
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

	private void readFiles(final Table table, final List<File> files)
			throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException {
		if (files.size() == 1) {
			table.setRowIteratorHandler(createRowIteratorHandler(CommonUtils.first(files)));
		} else {
			final List<RowIteratorHandler> handlers = files.stream().map(file -> {
				return createRowIteratorHandler(file);
			}).collect(Collectors.toList());
			table.setRowIteratorHandler(new CombinedRowIteratorHandler(handlers));
		}
	}

	private RowIteratorHandler createRowIteratorHandler(final File file) {
		final WorkbookFileType workbookFileType = WorkbookFileType.parse(file);
		if (workbookFileType.isTextFile()) {
			if (workbookFileType.isCsv()) {
				return new CsvRowIteratorHandler(file, getCsvEncoding(), this.getCsvSkipHeaderRowsSize(),
						createRowValueConverter());
			} else if (workbookFileType.isXml()) {
				return new XmlRowIteratorHandler(file, createRowValueConverter());
			} else if (workbookFileType.isToml()) {
				return new TomlRowIteratorHandler(file, this.getTomlConverter(), createRowValueConverter());
			} else if (workbookFileType.isYaml()) {
				return new YamlRowIteratorHandler(file, this.getYamlConverter(), createRowValueConverter());
			} else {
				return new JsonRowIteratorHandler(file, this.getJsonConverter(), createRowValueConverter());
			}
		} else {
			return new ExcelRowIteratorHandler(file, this.getExcelSkipHeaderRowsSize(), createRowValueConverter());
		}
	}

	protected void readFileAsXml(final Table table, final File file, final WorkbookFileType workbookFileType)
			throws XMLStreamException, FileNotFoundException {
		final XmlReaderOptions options = new XmlReaderOptions();
		options.setRowValueConverter(createRowValueConverter());
		table.loadXml(file, options);
	}

	@Override
	public void setFiles(File... obj) {
		this.files = PropertyUtils.convertArray(obj);
	}

	/**
	 * JDBCのバッチ実行のサイズを設定します
	 * 
	 * @param batchSize JDBCのバッチ実行のサイズ
	 */
	public void setDmlBatchSize(int batchSize) {
		this.getTableOptions().setDmlBatchSize(batchSize);
	}

	public void setCommitPerTable(final boolean bool) {
		this.getTableOptions().setCommitPerTable(bool);
	}
}
