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
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.FileFilterProperty;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.JsonConverterProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputFileTypeProperty;
import com.sqlapp.data.db.command.properties.RemoveOriginalFileProperty;
import com.sqlapp.data.db.command.properties.SheetNameProperty;
import com.sqlapp.data.db.command.properties.YamlConverterProperty;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowIteratorHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.util.file.TextFileWriter;

import lombok.Getter;
import lombok.Setter;

/**
 * Excel,CSV,Jsonのファイルを相互変換するためのコマンド
 * 
 * @author tatsuo satoh
 *
 */
@Getter
@Setter
public class ConvertDataCommand extends AbstractCommand implements FilesProperty, DirectoryProperty, FileFilterProperty,
		OutputFileTypeProperty, OutputDirectoryProperty, SheetNameProperty, CsvEncodingProperty, JsonConverterProperty,
		YamlConverterProperty, RemoveOriginalFileProperty {
	/** input files */
	private List<File> files = null;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;
	/**
	 * Output Directory
	 */
	private File directory = null;

	private String csvEncoding = Charset.defaultCharset().toString();

	private JsonConverter jsonConverter = createJsonConverter();

	private YamlConverter yamlConverter = createYamlConverter();

	private boolean recursive = false;

	private String sheetName = "TABLE";
	/**
	 * Output File Type
	 */
	private DataFormat outputFileType = DataFormat.EXCEL;

	private Converters converters = new Converters();

	private boolean removeOriginalFile = false;

	/**
	 * Output Directory
	 */
	private File outputDirectory = null;

	public ConvertDataCommand() {
	}

	@Override
	protected void doRun() {
		validateOutputFileType();
		List<File> list = getTargetFiles();
		for (File file : list) {
			DataFormat workbookFileType = DataFormat.parse(file);
			if (workbookFileType == null) {
				throw new IllegalArgumentException("Unsupported data file format: " + file);
			}
			if (workbookFileType.isToml()) {
				throw new IllegalArgumentException(
						"TOML input is not supported for data conversion because TOML has no root array: " + file);
			}
			Table[] table = new Table[1];
			table[0] = new Table();
			if (workbookFileType.isXml()) {
				execute(() -> {
					table[0] = SchemaUtils.readXml(file);
				});
			} else {
				final RowIteratorHandler rowIteratorHandler = FileRowIteratorFactory.create(file, getCsvEncoding(),
						0, 0, getJsonConverter(), getYamlConverter(), new TomlConverter(), (row, column, value) -> value);
				table[0].setRowIteratorHandler(rowIteratorHandler);
			}
			execute(() -> {
				File tempFile = null;
				try {
					final OutputFiles outputFiles = createOutputFiles(file);
					tempFile = outputFiles.temporary();
					final File outputFile = outputFiles.output();
					if (this.getOutputFileType().isWorkbook()) {
						readAll(table[0]);
						writeTableAsExcel(tempFile, table[0], this.getOutputFileType());
					} else if (this.getOutputFileType().isCsv()) {
						readAll(table[0]);
						writeTableAsCsv(tempFile, table[0], this.getOutputFileType());
					} else if (this.getOutputFileType().isJson()) {
						writeTableAsJson(tempFile, table[0], this.getOutputFileType());
					} else if (this.getOutputFileType().isJsonl()) {
						writeTableAsJsonl(tempFile, table[0], this.getOutputFileType());
					} else if (this.getOutputFileType().isYaml()) {
						writeTableAsYaml(tempFile, table[0], this.getOutputFileType());
					} else {
						table[0].writeXml(tempFile);
					}
					moveOutput(tempFile, outputFile);
					if (this.isRemoveOriginalFile()) {
						Files.delete(file.toPath());
					}
				} catch (Exception e) {
					if (tempFile != null) {
						try {
							Files.deleteIfExists(tempFile.toPath());
						} catch (final IOException cleanupFailure) {
							e.addSuppressed(cleanupFailure);
						}
					}
					throw e;
				}
			});
		}
	}

	private OutputFiles createOutputFiles(final File input) throws IOException {
		final File inputParent = input.getAbsoluteFile().getParentFile();
		final File outputParent;
		if (getOutputDirectory() != null
				&& !CommonUtils.eq(getOutputDirectory(), getDirectory())
				&& !CommonUtils.eq(getOutputDirectory(), input.getParentFile())) {
			outputParent = new File(getOutputDirectory(), inputParent.getName());
		} else {
			outputParent = inputParent;
		}
		Files.createDirectories(outputParent.toPath());
		final String baseName = FileUtils.getFileNameWithoutExtension(input.getName());
		final String temporaryPrefix = baseName.length() >= 3 ? baseName : (baseName + "___").substring(0, 3);
		final String extension = "." + getOutputFileType().getFileExtension();
		final File temporary = Files.createTempFile(outputParent.toPath(), temporaryPrefix, extension).toFile();
		final File output = new File(outputParent, baseName + extension);
		return new OutputFiles(temporary, output);
	}

	private record OutputFiles(File temporary, File output) {
	}

	private void validateOutputFileType() {
		if (getOutputFileType() == null) {
			throw new IllegalArgumentException("Output data format is required.");
		}
		if (getOutputFileType().isToml()) {
			throw new IllegalArgumentException(
					"TOML output is not supported because TOML has no root array.");
		}
	}

	private static void moveOutput(final File temporary, final File output) throws IOException {
		try {
			Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (final AtomicMoveNotSupportedException e) {
			Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void readAll(Table table) {
		for (@SuppressWarnings("unused")
		Row row : table.getRows()) {

		}
	}

	@SuppressWarnings("unchecked")
	private void writeTableAsCsv(File file, Table table, DataFormat workbookFileType) throws Exception {
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
				BufferedWriter bw = new BufferedWriter(writer);
				TextFileWriter csvWriter = workbookFileType.createCsvListWriter(bw)) {
			List<String> headers = table.getColumns().stream().map(c -> c.getName()).collect(Collectors.toList());
			csvWriter.writeHeader(headers.toArray(new String[0]));
			for (Row row : table.getRows()) {
				String[] values = new String[table.getColumns().size()];
				int i = 0;
				boolean set = false;
				for (Column column : table.getColumns()) {
					Object value = row.get(column);
					String text = column.getFormatter().format(value);
					values[i++] = text;
					if (!CommonUtils.isEmpty(text)) {
						set = true;
					}
				}
				if (set) {
					csvWriter.writeRow(values);
				}
			}
		}
	}

	private void writeTableAsJson(File file, Table table, DataFormat workbookFileType)
			throws IOException, XMLStreamException {
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			bw.write("[");
			boolean first = true;
			for (Row row : table.getRows()) {
				Map<String, Object> map = row.getValuesAsMapWithoutNullValue();
				if (map.isEmpty()) {
					continue;
				}
				String text = getJsonConverter().toJsonString(map);
				if (!first) {
					bw.write(",\n");
				} else {
					bw.write("\n");
					first = false;
				}
				bw.write(text);
			}
			if (!first) {
				bw.write("\n");
			}
			bw.write("]");
		}
	}

	private void writeTableAsJsonl(File file, Table table, DataFormat workbookFileType)
			throws IOException, XMLStreamException {
		JsonConverter converter = getJsonConverter().clone();
		converter.setIndentOutput(false);
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			boolean first = true;
			for (Row row : table.getRows()) {
				Map<String, Object> map = row.getValuesAsMapWithoutNullValue();
				if (map.isEmpty()) {
					continue;
				}
				String text = converter.toJsonString(map);
				if (!first) {
					bw.write("\n");
				} else {
					first = false;
				}
				bw.write(text);
			}
		}
	}

	private void writeTableAsYaml(File file, Table table, DataFormat workbookFileType)
			throws IOException, XMLStreamException {
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF8");
				BufferedWriter bw = new BufferedWriter(writer);) {
			bw.write("---");
			for (Row row : table.getRows()) {
				Map<String, Object> map = row.getValuesAsMapWithoutNullValue();
				if (map.isEmpty()) {
					continue;
				}
				String text = getYamlConverter().toJsonString(map);
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
			}
		}
	}

	private void writeTableAsExcel(File file, Table table, DataFormat workbookFileType)
			throws FileNotFoundException, IOException, EncryptedDocumentException, InvalidFormatException {
		try (Workbook workbook = workbookFileType.createWorkbook()) {
			Sheet sheet = ExcelUtils.getFirstOrCreateSeet(workbook, this.getSheetName());
			int rownum = 0;
			org.apache.poi.ss.usermodel.Row headerRow = ExcelUtils.getOrCreateRow(sheet, rownum++);
			int cellnum = 0;
			CreationHelper helper = workbook.getCreationHelper();
			for (Column column : table.getColumns()) {
				Cell cell = ExcelUtils.getOrCreateCell(headerRow, cellnum++);
				ExcelUtils.setCell(getConverters(), workbook, cell, column.getName());
			}
			for (Row row : table.getRows()) {
				org.apache.poi.ss.usermodel.Row dataRow = ExcelUtils.getOrCreateRow(sheet, rownum++);
				cellnum = 0;
				for (Column column : table.getColumns()) {
					Object obj = row.get(column);
					if (obj != null) {
						Cell cell = ExcelUtils.getOrCreateCell(dataRow, cellnum);
						ExcelUtils.setCell(getConverters(), workbook, cell, obj);
					}
					cellnum++;
				}
			}
			cellnum = 0;
			for (Column column : table.getColumns()) {
				sheet.autoSizeColumn(cellnum);
				if (column.getRemarks() != null) {
					Cell cell = ExcelUtils.getOrCreateCell(headerRow, cellnum);
					ExcelUtils.setComment(helper, cell, column.getRemarks());
				}
				cellnum++;
			}
			ExcelUtils.writeWorkbook(workbook, file);
		}
	}

	private List<File> getTargetFiles() {
		List<File> list = CommonUtils.list();
		if (this.files != null) {
			// files優先
			for (File file : files) {
				findFiles(file, list, true);
			}
			return list;
		}
		findFiles(this.getDirectory(), list, this.getDirectory() != null);
		return list;
	}

	private void findFiles(final File file, final List<File> list, final boolean required) {
		if (file == null) {
			return;
		}
		if (!file.exists()) {
			if (required) {
				throw new IllegalArgumentException("Input file or directory does not exist: " + file);
			}
			return;
		}
		if (file.isDirectory()) {
			File[] children = file.listFiles(f -> true);
			if (children == null) {
				throw new UncheckedIOException(new IOException("Cannot list input directory: " + file));
			}
			for (File child : children) {
				if (child.isFile()) {
					addFile(child, list, false);
				} else {
					if (isRecursive()) {
						findFiles(child, list, false);
					}
				}
			}
		} else {
			addFile(file, list, required);
		}
	}

	private void addFile(final File file, final List<File> list, final boolean required) {
		if (!file.exists()) {
			if (required) {
				throw new IllegalArgumentException("Input file does not exist: " + file);
			}
			return;
		}
		DataFormat workbookFileType = DataFormat.parse(file);
		if (workbookFileType == null && required) {
			throw new IllegalArgumentException("Unsupported data file format: " + file);
		}
		if (workbookFileType != null && workbookFileType != this.getOutputFileType()) {
			if (getFileFilter().test(file)) {
				list.add(file);
			}
		}
	}
}
