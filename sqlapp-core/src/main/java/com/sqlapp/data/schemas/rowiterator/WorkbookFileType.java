/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
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
import java.util.Map;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookProvider;

import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.util.file.FileType;
import com.sqlapp.util.file.TextFileReader;
import com.sqlapp.util.file.TextFileWriter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectReader;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;
import tools.jackson.dataformat.yaml.YAMLMapper;

public enum WorkbookFileType {
	EXCEL2003() {
		@Override
		public String getFileExtension() {
			return "xls";
		}

		@Override
		public Workbook createWorkbook() {
			return new org.apache.poi.hssf.usermodel.HSSFWorkbook();
		}

		@Override
		public boolean isWorkbook() {
			return true;
		}

		@Override
		public Workbook createWorkBook(final File file, final String password, final boolean readonly)
				throws EncryptedDocumentException, InvalidFormatException, IOException {
			WorkbookProvider factory = new org.apache.poi.hssf.usermodel.HSSFWorkbookFactory();
			return factory.create(file, password, readonly);
		}

		@Override
		public Workbook createWorkBook(final InputStream is)
				throws EncryptedDocumentException, InvalidFormatException, IOException {
			WorkbookProvider factory = new org.apache.poi.hssf.usermodel.HSSFWorkbookFactory();
			return factory.create(is);
		}
	},
	EXCEL() {
		@Override
		public String getFileExtension() {
			return "xlsx";
		}

		@Override
		public Workbook createWorkbook() {
			return new org.apache.poi.xssf.usermodel.XSSFWorkbook();
		}

		@Override
		public boolean isWorkbook() {
			return true;
		}

		@Override
		public Workbook createWorkBook(final File file, final String password, final boolean readonly)
				throws EncryptedDocumentException, InvalidFormatException, IOException {
			WorkbookProvider factory = new org.apache.poi.xssf.usermodel.XSSFWorkbookFactory();
			return factory.create(file, password, readonly);
		}

		@Override
		public Workbook createWorkBook(final InputStream is)
				throws EncryptedDocumentException, InvalidFormatException, IOException {
			WorkbookProvider factory = new org.apache.poi.xssf.usermodel.XSSFWorkbookFactory();
			return factory.create(is);
		}
	},
	TSV() {
		@Override
		public String getFileExtension() {
			return "tsv";
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isCsv() {
			return true;
		}

		@Override
		public ObjectReader getObjectReader() {
			return TSV_READER;
		}

		@Override
		public TextFileWriter createCsvListWriter(final Writer writer) {
			return new TextFileWriter(getFileType(), writer, setting -> {
			});
		}

		@Override
		public TextFileReader createCsvListReader(final Reader reader) {
			return new TextFileReader(getFileType(), reader, setting -> {
			});
		}

		@Override
		public FileType getFileType() {
			return FileType.TSV;
		}
	},
	CSV() {
		@Override
		public String getFileExtension() {
			return "csv";
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isCsv() {
			return true;
		}

		@Override
		public ObjectReader getObjectReader() {
			return CSV_READER;
		}

		@Override
		public TextFileWriter createCsvListWriter(final Writer writer) {
			return new TextFileWriter(getFileType(), writer, setting -> {
			});
		}

		@Override
		public TextFileReader createCsvListReader(final Reader reader) {
			return new TextFileReader(getFileType(), reader, setting -> {
			});
		}

		@Override
		public FileType getFileType() {
			return FileType.CSV;
		}
	},
	SSV() {
		@Override
		public String getFileExtension() {
			return "ssv";
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isCsv() {
			return true;
		}

		@Override
		public ObjectReader getObjectReader() {
			return SSV_READER;
		}

		@Override
		public TextFileWriter createCsvListWriter(final Writer writer) {
			return new TextFileWriter(getFileType(), writer, setting -> {
			});
		}

		@Override
		public TextFileReader createCsvListReader(final Reader reader) {
			return new TextFileReader(getFileType(), reader, setting -> {
			});
		}

		@Override
		public FileType getFileType() {
			return FileType.SSV;
		}
	},
	XML() {
		@Override
		public String getFileExtension() {
			return "xml";
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isXml() {
			return true;
		}
	},
	JSON() {
		@Override
		public String getFileExtension() {
			return "json";
		}

		@Override
		public String[] getFileExtensions() {
			return new String[] { "jsonnl" };
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isJson() {
			return true;
		}

		@Override
		public JsonConverter createJsonConverter() {
			return new JsonConverter();
		}
	},
	JSONL() {
		@Override
		public String getFileExtension() {
			return "jsonl";
		}

		@Override
		public String[] getFileExtensions() {
			return new String[] { "ndjson" };
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isJsonl() {
			return true;
		}

		@Override
		public JsonConverter createJsonConverter() {
			return new JsonConverter();
		}
	},
	TOML() {
		@Override
		public String getFileExtension() {
			return "toml";
		}

		@Override
		public String[] getFileExtensions() {
			return new String[] { "tml" };
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isToml() {
			return true;
		}

		@Override
		public JsonConverter createJsonConverter() {
			return new TomlConverter();
		}
	},
	YAML() {
		@Override
		public String getFileExtension() {
			return "yaml";
		}

		@Override
		public ObjectReader getObjectReader() {
			return YAML_READER;
		}

		@Override
		public String[] getFileExtensions() {
			return new String[] { "yml" };
		}

		@Override
		public boolean isTextFile() {
			return true;
		}

		@Override
		public boolean isYaml() {
			return true;
		}

		@Override
		public JsonConverter createJsonConverter() {
			return new YamlConverter();
		}
	},;

	public String getFileExtension() {
		return null;
	}

	public String[] getFileExtensions() {
		return new String[0];
	}

	public boolean isJson() {
		return false;
	}

	public boolean isJsonl() {
		return false;
	}

	public boolean isTextFile() {
		return false;
	}

	public boolean isCsv() {
		return false;
	}

	public boolean isTsv() {
		return false;
	}

	public boolean isSsv() {
		return false;
	}

	public boolean isXml() {
		return false;
	}

	public boolean isToml() {
		return false;
	}

	public boolean isYaml() {
		return false;
	}

	public boolean isWorkbook() {
		return false;
	}

	/**
	 * ワークブックを作成します。
	 */
	public Workbook createWorkbook() {
		return null;
	}

	public FileType getFileType() {
		return null;
	}

	private static final CsvMapper CSV_MAPPER = new CsvMapper();
	private static final ObjectReader MAP_READER = CSV_MAPPER.readerFor(new TypeReference<Map<String, String>>() {
	});
	private static final CsvSchema CSV_SCHEMA = CsvSchema.emptySchema().withHeader();
	private static final CsvSchema TSV_SCHEMA = CsvSchema.emptySchema().withHeader().withColumnSeparator('	');
	private static final CsvSchema SSV_SCHEMA = CsvSchema.emptySchema().withHeader().withColumnSeparator(' ');
	private static final ObjectReader CSV_READER = MAP_READER.with(CSV_SCHEMA);
	private static final ObjectReader TSV_READER = MAP_READER.with(TSV_SCHEMA);
	private static final ObjectReader SSV_READER = MAP_READER.with(SSV_SCHEMA);
	private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
	private static final ObjectReader YAML_READER = YAML_MAPPER.readerFor(new TypeReference<Map<String, String>>() {
	});

	public ObjectReader getObjectReader() {
		return null;
	}

	/**
	 * CSV List Writerを作成します。
	 */
	public TextFileWriter createCsvListWriter(final Writer writer) {
		return null;
	}

	/**
	 * CSV List Writerを作成します。
	 * 
	 * @throws IOException
	 */
	public TextFileWriter createCsvListWriter(final File file, final String charset) throws IOException {
		if (this.isCsv()) {
			return createCsvListWriter(
					new BufferedWriter(new FileWriter(file, Charset.forName(charset != null ? charset : "UTF8"))));
		}
		return null;
	}

	/**
	 * CSV List Readerを作成します。
	 * 
	 * @throws IOException
	 */
	public TextFileReader createCsvListReader(final File file, final String charset) throws IOException {
		if (this.isCsv()) {
			return createCsvListReader(
					new BufferedReader(new FileReader(file, Charset.forName(charset != null ? charset : "UTF8"))));
		}
		return null;
	}

	/**
	 * CSV List Readerを作成します。
	 */
	public TextFileReader createCsvListReader(final Reader reader) {
		return null;
	}

	public JsonConverter createJsonConverter() {
		return null;
	}

	/**
	 * Create Workbook
	 * 
	 * @param file File
	 * @return Workbook
	 * @throws EncryptedDocumentException
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public Workbook createWorkBook(final File file)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		return createWorkBook(file, null, false);
	}

	/**
	 * Create Workbook
	 * 
	 * @param file     File
	 * @param password password
	 * @param readonly read only
	 * @return Workbook
	 * @throws EncryptedDocumentException
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public Workbook createWorkBook(final File file, final String password, final boolean readonly)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		return null;
	}

	/**
	 * Create Workbook
	 * 
	 * @param file     File
	 * @param readonly read only
	 * @return Workbook
	 * @throws EncryptedDocumentException
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	public Workbook createWorkBook(final File file, final boolean readonly)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		return createWorkBook(file, null, readonly);
	}

	public Workbook createWorkBook(final InputStream is)
			throws EncryptedDocumentException, InvalidFormatException, IOException {
		return null;
	}

	public boolean match(final String text) {
		if (text == null) {
			return false;
		}
		final String lowername = text.toLowerCase();
		if (lowername.endsWith("." + this.getFileExtension())) {
			return true;
		}
		if (text.equalsIgnoreCase(this.toString())) {
			return true;
		}
		for (String ext : this.getFileExtensions()) {
			if (lowername.endsWith("." + ext)) {
				return true;
			}
		}
		return false;
	}

	public static WorkbookFileType parse(final String text) {
		if (text == null) {
			return null;
		}
		final String lowername = text.trim().toLowerCase();
		for (final WorkbookFileType val : values()) {
			if (lowername.endsWith("." + val.getFileExtension())) {
				return val;
			}
			if (text.equalsIgnoreCase(val.toString())) {
				return val;
			}
		}
		for (final WorkbookFileType val : values()) {
			for (String ext : val.getFileExtensions()) {
				if (lowername.endsWith("." + ext)) {
					return val;
				}
			}
		}
		for (final WorkbookFileType val : values()) {
			if (val.getFileExtension().equals(lowername)) {
				return val;
			}
		}
		for (final WorkbookFileType val : values()) {
			for (String ext : val.getFileExtensions()) {
				if (ext.equals(text)) {
					return val;
				}
			}
		}
		return null;
	}

	public static WorkbookFileType parse(final File file) {
		if (file == null) {
			return null;
		}
		return parse(file.getName());
	}

	public static WorkbookFileType parse(final Path path) {
		if (path == null) {
			return null;
		}
		return parse(path.toFile().getName());
	}

}
