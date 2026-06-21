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
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.JsonConverterProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.db.command.properties.YamlConverterProperty;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaCollection;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.exceptions.InvalidFileTypeException;
import com.sqlapp.exceptions.InvalidPropertyException;
import com.sqlapp.util.AbstractIterator;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.YamlConverter;
import com.sqlapp.util.file.TextFileReader;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractSchemaFileCommand extends AbstractCommand
		implements CsvEncodingProperty, TargetFileProperty, JsonConverterProperty, YamlConverterProperty {

	/**
	 * file
	 */
	private File targetFile;

	private String dictionaryFileType = "xlsx";

	/** csvFileCharset */
	private String csvEncoding = Charset.defaultCharset().toString();

	private JsonConverter jsonConverter = new JsonConverter();

	private YamlConverter yamlConverter = new YamlConverter();

	private String[] keywords = new String[] { SchemaProperties.DISPLAY_NAME.getLabel(),
			SchemaProperties.DISPLAY_REMARKS.getLabel() };

	private Map<String, Integer> keywordsMap = CommonUtils.map();

	private Catalog catalog = null;

	/**
	 * @return the keywords
	 */
	public String[] getKeywords() {
		return keywords;
	}

	protected AbstractSchemaFileCommand() {
		jsonConverter.setIndentOutput(true);
		for (int i = 0; i < this.getKeywords().length; i++) {
			keywordsMap.put(keywords[i], i);
		}
	}

	@Override
	protected void doRun() {
		DbCommonObject<?> obj = null;
		if (this.getCatalog() == null) {
			try {
				obj = SchemaUtils.readXml(targetFile);
			} catch (FileNotFoundException e) {
				throw new CommandException("targetFile=" + targetFile, e);
			} catch (IOException e) {
				throw new CommandException("targetFile=" + targetFile, e);
			}
		} else {
			obj = this.getCatalog();
		}
		try {
			if (obj instanceof Schema) {
				create(((Schema) obj).toCatalog());
			} else if (obj instanceof SchemaCollection) {
				create(((SchemaCollection) obj).toCatalog());
			} else if (obj instanceof Catalog) {
				create((Catalog) obj);
			} else {
				throw new IllegalArgumentException("targetFile type must be a Schema or SchemaCollection or Catalog");
			}
		} catch (Exception e) {
			throw new CommandException("targetFile=" + targetFile, e);
		}
	}

	protected abstract void create(Catalog catalog) throws Exception;

	protected String getFullName(Object obj, boolean withSchemaName) {
		String fullName = null;
		if (withSchemaName) {
			fullName = HtmlUtils.objectFullName(obj);
		} else {
			fullName = HtmlUtils.objectFullNameWithoutSchemaName(obj);
		}
		return fullName;
	}

	protected String getName(Object obj) {
		String name = ((NameProperty<?>) obj).getName();
		return name;
	}

	protected File loadProperties(File directory, MenuDefinition menuDefinition, Properties properties)
			throws Exception {
		String filename = menuDefinition.toString().toLowerCase();
		if (CommonUtils.isEmpty(directory)) {
			return null;
		}
		File[] files = directory.listFiles((d, name) -> {
			return name.startsWith(filename + ".");
		});
		if (CommonUtils.isEmpty(files)) {
			return null;
		}
		if (files.length > 1) {
			throw new DuplicatePropertyFilesException(files);
		}
		File file = CommonUtils.first(files);
		if (file.exists()) {
			try (InputStream is = new FileInputStream(file)) {
				if (file.getAbsolutePath().endsWith(".properties")) {
					properties.load(is);
				} else if (file.getAbsolutePath().endsWith(".xml")) {
					properties.loadFromXML(is);
				} else {
					readOtherFiles(file, is, properties);
				}
			}
			List<MenuDefinition> menuDefinitions = menuDefinition.getNest();
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				int current = entry.getKey().toString().split("\\.").length;
				if (current > (menuDefinitions.size() + 2)) {
					throw new InvalidPropertyException(entry.getKey().toString(), entry.getValue());
				}
			}
		}
		return file;
	}

	private void readOtherFiles(File file, InputStream is, Properties properties) throws Exception {
		DataFormat workbookFileType = DataFormat.parse(file);
		if (workbookFileType.isTextFile() && workbookFileType.isCsv()) {
			readCsvFile(workbookFileType, file, is, properties);
		} else if (workbookFileType.isWorkbook()) {
			readWorkbookFile(workbookFileType, file, is, properties);
		} else if (workbookFileType.isJson()) {
			readJsonFile(workbookFileType, file, is, properties);
		} else if (workbookFileType.isJsonl()) {
			readJsonlFile(workbookFileType, file, is, properties);
		} else if (workbookFileType.isYaml()) {
			readYamlFile(workbookFileType, file, is, properties);
		} else {
			throw new InvalidFileTypeException(file);
		}
	}

	private void readWorkbookFile(DataFormat workbookFileType, File file, InputStream is, Properties properties)
			throws UnsupportedEncodingException, IOException, EncryptedDocumentException, InvalidFormatException {
		Workbook workbook = DataFormat.parse(file).createWorkBook(is);
		int numberOdSheets = workbook.getNumberOfSheets();
		for (int sheetNo = 0; sheetNo < numberOdSheets; sheetNo++) {
			Sheet sheet = workbook.getSheetAt(sheetNo);
			int rowIndex = sheet.getFirstRowNum();
			Row row = sheet.getRow(rowIndex);
			int lastRowNum = sheet.getLastRowNum();
			short lastCellNum = row.getLastCellNum();
			String[] headers = new String[lastCellNum + 1];
			MenuDefinition[] headerDefs = new MenuDefinition[headers.length];
			int keywordCount = 0;
			for (int i = 0; i < headers.length; i++) {
				String header = ExcelUtils.getStringCellValue(row.getCell(i));
				if (header == null) {
					continue;
				}
				MenuDefinition def = MenuDefinition.parse(header);
				if (def != null) {
					headerDefs[i] = def;
					headers[i] = def.toString();
				} else {
					headers[i] = getKeywords()[keywordCount++];
				}
			}
			for (int i = rowIndex + 1; i <= lastRowNum; i++) {
				StringBuilder builder = new StringBuilder();
				row = sheet.getRow(i);
				for (int j = 0; j < headers.length; j++) {
					String value = ExcelUtils.getStringCellValue(row.getCell(j));
					if (value == null) {
						value = "";
					}
					MenuDefinition headerDef = headerDefs[j];
					if (headerDef != null) {
						if (!CommonUtils.isEmpty(value)) {
							builder.append(value);
							builder.append(".");
						}
					} else {
						String header = headers[j];
						if (header != null) {
							String key = builder.toString() + header;
							put(properties, key, value);
						}
					}
				}
			}
		}
	}

	private void put(Properties properties, String key, String value) {
		Object original = properties.get(key);
		if (original == null || "".equals(original)) {
			properties.put(key, value);
		}
	}

	private void readCsvFile(DataFormat workbookFileType, File file, InputStream is, Properties properties)
			throws Exception {
		try (Reader reader = new InputStreamReader(is, this.getCsvEncoding());
				BufferedReader br = new BufferedReader(reader);
				TextFileReader csvListReader = workbookFileType.createCsvListReader(br);) {
			final String[] headers = csvListReader.read();
			final MenuDefinition[] headerDefs = new MenuDefinition[headers.length];
			int keywordCount = 0;
			for (int i = 0; i < headers.length; i++) {
				String header = headers[i];
				if (header == null) {
					continue;
				}
				final MenuDefinition def = MenuDefinition.parse(header);
				if (def != null) {
					headerDefs[i] = def;
					headers[i] = def.toString();
				} else {
					headers[i] = getKeywords()[keywordCount++];
				}
			}
			String[] list = csvListReader.read();
			while (list != null) {
				list = csvListReader.read();
				String text = CommonUtils.first(list);
				if (CommonUtils.isEmpty(text)) {
					continue;
				}
				if (text.startsWith("#")) {
					continue;
				}
				for (int i = 0; i < list.length; i++) {
					StringBuilder builder = new StringBuilder();
					String value = list[i];
					if (value == null) {
						value = "";
					}
					if (i < headers.length) {
						MenuDefinition headerDef = headerDefs[i];
						if (headerDef != null) {
							if (!CommonUtils.isEmpty(value)) {
								builder.append(value);
								builder.append(".");
							}
						} else {
							String header = headers[i];
							if (header != null) {
								String key = builder.toString() + header;
								put(properties, key, value);
							}
						}
					}
				}
			}
		}
	}

	private void readJsonFile(DataFormat workbookFileType, File file, InputStream is, Properties properties)
			throws Exception {
		Object obj = getJsonConverter().fromJsonString(is, Object.class);
		readFromObject(workbookFileType, obj, properties);
	}

	private void readYamlFile(DataFormat workbookFileType, File file, InputStream is, Properties properties)
			throws Exception {
		Object obj = getYamlConverter().fromJsonString(is, Object.class);
		readFromObject(workbookFileType, obj, properties);
	}

	private void readFromObject(DataFormat workbookFileType, Object obj, Properties properties) throws Exception {
		if (obj instanceof Collection || obj.getClass().isArray()) {
			AbstractIterator<Object> itr = new AbstractIterator<Object>() {
				@Override
				protected void handle(Object obj, int index) throws Exception {
					if (obj instanceof Map) {
						@SuppressWarnings("rawtypes")
						Map<String, String> map = toStringMap((Map) obj);
						properties.putAll(map);
					}
				}
			};
			itr.execute(obj);
		} else {
			@SuppressWarnings("rawtypes")
			Map<String, String> map = toStringMap((Map) obj);
			properties.putAll(map);
		}
	}

	private void readJsonlFile(DataFormat workbookFileType, File file, InputStream is, Properties properties)
			throws UnsupportedEncodingException, IOException {
		try (Reader reader = new InputStreamReader(is, this.getCsvEncoding());
				BufferedReader br = new BufferedReader(reader);) {
			String data = null;
			while ((data = br.readLine()) != null) {
				Object obj = getJsonConverter().fromJsonString(data, Object.class);
				@SuppressWarnings("rawtypes")
				Map<String, String> map = toStringMap((Map) obj);
				properties.putAll(map);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> toStringMap(@SuppressWarnings("rawtypes") Map map) {
		Map<String, String> result = CommonUtils.linkedMap();
		String path = null;
		map.forEach((k, v) -> {
			toStringList(path, k.toString(), v, result);
		});
		return result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void toStringList(String path, String key, Object value, Map<String, String> result) {
		String currentPath = createPath(path, key);
		if ((value instanceof String) || (value instanceof Number) || (value instanceof Boolean)) {
			result.put(currentPath, value.toString());
			return;
		} else if (value instanceof Map) {
			((Map) value).forEach((k, v) -> {
				toStringList(currentPath, k.toString(), v, result);
			});
		}
	}

	private String createPath(String path, String key) {
		if (CommonUtils.isEmpty(path)) {
			path = key;
		} else {
			path = path + "." + key;
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
	 * @return the keywordsMap
	 */
	protected Map<String, Integer> getKeywordsMap() {
		return keywordsMap;
	}

}
