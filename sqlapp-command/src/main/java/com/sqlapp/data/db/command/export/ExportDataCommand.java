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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
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

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.properties.ConvertersProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputFileTypeProperty;
import com.sqlapp.data.db.command.properties.SheetNameProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SchemaReader;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Synonym;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableNameRowCollectionFilter;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.data.schemas.rowiterator.JdbcDynamicRowIteratorHandler;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.DoubleKeyMap;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.file.TextFileWriter;

import lombok.Getter;
import lombok.Setter;

/**
 * Exportコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public class ExportDataCommand extends AbstractExportCommand
		implements OutputFileTypeProperty, OutputDirectoryProperty, SheetNameProperty, ConvertersProperty {
	/**
	 * Output Directory
	 */
	private File outputDirectory = new File(".");
	/**
	 * Output File Type
	 */
	private DataFormat outputFileType = DataFormat.EXCEL;

	private String sheetName = "TABLE";

	private Converters converters = new Converters();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@Override
	protected void doRun() {
		info("includeSchemas=", Arrays.toString(getIncludeSchemas()));
		info("excludeSchemas=", Arrays.toString(getExcludeSchemas()));
		info("includeTables=", Arrays.toString(getIncludeTables()));
		info("excludeTables=", Arrays.toString(getExcludeTables()));
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final SchemaReader schemaReader = getSchemaReader(connection, dialect);
			Map<String, Schema> schemaMap = this.getSchemas(connection, dialect, schemaReader, s -> true);
			final JdbcDynamicRowIteratorHandler rowIteratorHandler = getRowIteratorHandler(connection);
			schemaMap.forEach((k, v) -> {
				v.setRowIteratorHandler(rowIteratorHandler);
			});
			if (!this.getOutputDirectory().exists()) {
				FileUtils.createParentDirectory(this.getOutputDirectory());
				this.getOutputDirectory().mkdir();
			}
			final DoubleKeyMap<String, String, Table> execTables = CommonUtils.doubleKeyMap();
			for (Map.Entry<String, Schema> entry : schemaMap.entrySet()) {
				String k = entry.getKey();
				Schema v = entry.getValue();
				File targetDirectory = null;
				if (this.isUseSchemaNameDirectory()) {
					final File file = new File(this.getOutputDirectory(), k);
					if (!file.exists()) {
						file.mkdirs();
						file.mkdir();
					}
					targetDirectory = file;
				} else {
					targetDirectory = this.getOutputDirectory();
				}
				for (final Table t : v.getTables()) {
					writeTableWithLog(targetDirectory, t, rowIteratorHandler, execTables);
				}
				for (final Synonym s : v.getSynonyms()) {
					final Table table = s.rootSynonym().getTable();
					if (table == null) {
						continue;
					}
					if (execTables.containsKey(table.getSchemaName(), table.getName())) {
						continue;
					}
					writeTableWithLog(targetDirectory, table, rowIteratorHandler, execTables);
				}
			}
		});
	}

	private void writeTableWithLog(File targetDirectory, final Table t, final JdbcDynamicRowIteratorHandler rowIterator,
			final DoubleKeyMap<String, String, Table> execTables) throws Exception {
		if (!rowIterator.getFilter().test(t.getRows())) {
			info(MESSAGE_SEPARATOR_START, t.getName(), " Export skipped.", MESSAGE_SEPARATOR_END);
			return;
		}
		final LocalDateTime startLocalTime = LocalDateTime.now();
		long start = System.currentTimeMillis();
		info(MESSAGE_SEPARATOR_START, t.getName(), " Export start. start=[", startLocalTime, "].",
				MESSAGE_SEPARATOR_END);
		long ret = writeTable(targetDirectory, t.getName(), t, this.getOutputFileType());
		String sql;
		sql = rowIterator.getResultSetIterator() != null ? rowIterator.getResultSetIterator().getSql() : "";
		info(sql);
		long end = System.currentTimeMillis();
		final LocalDateTime endLocalTime = LocalDateTime.now();
		info(MESSAGE_SEPARATOR_START, t.getName(), " ", ret, " rows export completed. end=[", endLocalTime, "]. [",
				(end - start), " ms].", MESSAGE_SEPARATOR_END);
		execTables.put(t.getSchemaName(), t.getName(), t);
	}

	private long writeTable(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws Exception {
		if (this.getOutputFileType().isTextFile()) {
			if (this.getOutputFileType().isCsv()) {
				return writeTableAsCsv(directory, filename, table, this.getOutputFileType());
			} else if (this.getOutputFileType().isXml()) {
				return writeTableAsXml(directory, filename, table, this.getOutputFileType());
			} else if (this.getOutputFileType().isJson()) {
				return writeTableAsJson(directory, filename, table, this.getOutputFileType());
			} else if (this.getOutputFileType().isJsonl()) {
				return writeTableAsJsonl(directory, filename, table, this.getOutputFileType());
			} else if (this.getOutputFileType().isToml()) {
				return writeTableAsToml(directory, filename, table, this.getOutputFileType());
			} else if (this.getOutputFileType().isYaml()) {
				return writeTableAsYaml(directory, filename, table, this.getOutputFileType());
			} else {
				return 0;
			}
		} else {
			return writeTableAsExcel(directory, filename, table, this.getOutputFileType());
		}
	}

	@SuppressWarnings("unchecked")
	private long writeTableAsCsv(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws Exception {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		long counter = 0;
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
				BufferedWriter bw = new BufferedWriter(writer);
				TextFileWriter csvWriter = workbookFileType.createCsvListWriter(bw)) {
			final List<String> headers = table.getColumns().stream().map(c -> c.getName()).collect(Collectors.toList());
			csvWriter.writeHeader(headers.toArray(new String[0]));
			final String[] values = new String[table.getColumns().size()];
			for (final Row row : table.getRows()) {
				int i = 0;
				for (final Column column : table.getColumns()) {
					final Object value = row.get(column);
					values[i++] = column.getConverter().convertString(value);
				}
				csvWriter.writeRow(values);
				counter++;
			}
		}
		return counter;
	}

	private long writeTableAsXml(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws IOException, XMLStreamException {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		return table.writeRowData(file);
	}

	private long writeTableAsJson(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws IOException, XMLStreamException {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		long counter = 0;
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			bw.write("[");
			boolean first = true;
			for (final Row row : table.getRows()) {
				final String text = getJsonConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				if (!first) {
					bw.write(",\n");
				} else {
					bw.write("\n");
					first = false;
				}
				bw.write(text);
				counter++;
			}
			if (!first) {
				bw.write("\n");
			}
			bw.write("]");
		}
		return counter;
	}

	private long writeTableAsJsonl(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws IOException, XMLStreamException {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		final JsonConverter converter = getJsonConverter().clone();
		converter.setIndentOutput(false);
		long counter = 0;
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			boolean first = true;
			for (final Row row : table.getRows()) {
				final String text = getJsonConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				if (!first) {
					bw.write("\n");
				} else {
					first = false;
				}
				bw.write(text);
				counter++;
			}
		}
		return counter;
	}

	private long writeTableAsToml(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws IOException, XMLStreamException {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		long counter = 0;
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			for (final Row row : table.getRows()) {
				final String text = getTomlConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				bw.write(text);
				counter++;
			}
		}
		return counter;
	}

	private long writeTableAsYaml(final File directory, final String filename, final Table table,
			final DataFormat workbookFileType) throws IOException, XMLStreamException {
		final File file = new File(directory, filename + "." + workbookFileType.getFileExtension());
		long counter = 0;
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			bw.write("---");
			for (final Row row : table.getRows()) {
				final String text = getYamlConverter().toJsonString(row.getValuesAsMapWithoutNullValue());
				String[] args = text.split("\n");
				for (int i = 1; i < args.length; i++) {
					bw.write("\n");
					if (i == 1) {
						bw.write("- ");
					} else {
						bw.write("  ");
					}
					bw.write(args[i]);
				}
				counter++;
			}
		}
		return counter;
	}

	private long writeTableAsExcel(final File directory, final String fileName, final Table table,
			final DataFormat workbookFileType)
			throws FileNotFoundException, IOException, EncryptedDocumentException, InvalidFormatException {
		final File file = new File(directory, fileName + "." + workbookFileType.getFileExtension());
		long counter = 0;
		try (Workbook workbook = createWorkbook(workbookFileType, file)) {
			Sheet sheet;
			if (file.exists()) {
				sheet = ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
				ExcelUtils.clearCellValues(sheet);
			} else {
				sheet = ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
			}
			int rownum = 0;
			final org.apache.poi.ss.usermodel.Row headerRow = ExcelUtils.getOrCreateRow(sheet, rownum++);
			int cellnum = 0;
			final CreationHelper helper = workbook.getCreationHelper();
			for (final Column column : table.getColumns()) {
				final Cell cell = ExcelUtils.getOrCreateCell(headerRow, cellnum++);
				ExcelUtils.setCell(converters, workbook, cell, column.getName());
			}
			for (final Row row : table.getRows()) {
				final org.apache.poi.ss.usermodel.Row dataRow = ExcelUtils.getOrCreateRow(sheet, rownum++);
				cellnum = 0;
				for (final Column column : table.getColumns()) {
					final Object obj = row.get(column);
					if (obj != null) {
						final Cell cell = ExcelUtils.getOrCreateCell(dataRow, cellnum);
						ExcelUtils.setCell(converters, workbook, cell, obj);
					}
					cellnum++;
				}
				counter++;
			}
			cellnum = 0;
			for (final Column column : table.getColumns()) {
				sheet.autoSizeColumn(cellnum);
				if (column.getRemarks() != null) {
					final Cell cell = ExcelUtils.getOrCreateCell(headerRow, cellnum);
					ExcelUtils.setComment(helper, cell, column.getRemarks());
				}
				cellnum++;
			}
			ExcelUtils.writeWorkbook(workbook, file);
		}
		return counter;
	}

	private Workbook createWorkbook(DataFormat workbookFileType, File file)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		if (file.exists()) {
			file.delete();
		}
		return workbookFileType.createWorkbook();
	}

	protected JdbcDynamicRowIteratorHandler getRowIteratorHandler(Connection connection) {
		final JdbcDynamicRowIteratorHandler rowIteratorHandler = new JdbcDynamicRowIteratorHandler(connection);
		rowIteratorHandler.setTableOptions(getTableOptions());
		final TableNameRowCollectionFilter filter = new TableNameRowCollectionFilter();
		filter.setIncludes(this.getIncludeTables());
		filter.setExcludes(this.getExcludeTables());
		rowIteratorHandler.setFilter(filter);
		return rowIteratorHandler;
	}

}
