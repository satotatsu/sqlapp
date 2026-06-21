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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.db.command.properties.DirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.RemoveOriginalFileProperty;
import com.sqlapp.data.db.command.util.ExcelCommandUtils;
import com.sqlapp.data.schemas.AbstractDbObject;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.SchemaProperties;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.properties.NameProperty;
import com.sqlapp.data.schemas.properties.RemarksProperty;
import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.data.schemas.rowiterator.ExcelUtils;
import com.sqlapp.exceptions.InvalidFileTypeException;
import com.sqlapp.exceptions.InvalidPropertyException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.LinkedProperties;
import com.sqlapp.util.file.TextFileWriter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDictionariesCommand extends AbstractSchemaFileCommand
		implements DirectoryProperty, OutputDirectoryProperty, RemoveOriginalFileProperty {

	private File directory = new File("./");
	private File outputDirectory = new File("./");

	private List<File> inputFiles = new ArrayList<>();

	private List<File> outputFiles = new ArrayList<>();

	private Map<MenuDefinition, Properties> propertiesMap = CommonUtils.linkedMap();

	private boolean dryRun = false;

	private Predicate<String> withSchema = (o) -> true;

	private boolean outputRemarksAsDisplayName = true;

	private boolean removeOriginalFile = true;

	@Override
	protected void create(Catalog catalog) {
		inputFiles.clear();
		outputFiles.clear();
		propertiesMap.clear();
		execute(() -> {
			for (MenuDefinition menuDefinition : MenuDefinition.values()) {
				createProperties(catalog, menuDefinition, (obj) -> HtmlUtils.objectFullName(obj));
			}
		});
	}

	protected void createProperties(Catalog catalog, MenuDefinition menuDefinition, Function<Object, String> nameFunc)
			throws Exception {
		List<AbstractDbObject<?>> list = menuDefinition.getDatas(catalog);
		if (list.isEmpty()) {
			return;
		}
		if (!(list.get(0) instanceof NameProperty)) {
			return;
		}
		String filename = menuDefinition.toString().toLowerCase();
		// Properties properties=new SortedProperties(new
		// StringComparator(this.keywordsMap));
		Properties fileProperties = new LinkedProperties();
		Properties mergeProperties = new LinkedProperties();
		File file = loadProperties(this.getDirectory(), menuDefinition, fileProperties);
		if (file != null) {
			inputFiles.add(file);
			String value = (String) fileProperties.get("PUBLIC.PRODUCTS.ACTIVE.displayName");
			System.out.print("1:" + value);
		}
		list.forEach(obj -> {
			String fullName = this.getFullName(obj, this.getWithSchema().test(filename));
			putProperty(fileProperties, fullName, obj, mergeProperties);
		});
		Set<String> keys = CommonUtils.treeSet();
		if (menuDefinition == MenuDefinition.Columns) {
			list.forEach(obj -> {
				String fullName = this.getFullName(obj, this.getWithSchema().test(filename));
				String columnNameWithTable = this.getColumnNameWithTable(obj);
				if (!CommonUtils.eq(fullName, columnNameWithTable)) {
					putProperty(fileProperties, columnNameWithTable, obj, mergeProperties);
				}
			});
			list.forEach(obj -> {
				String fullName = this.getFullName(obj, this.getWithSchema().test(filename));
				String columnNameWithTable = this.getColumnNameWithTable(obj);
				String name = this.getName(obj);
				if (!CommonUtils.eq(fullName, name) && !CommonUtils.eq(columnNameWithTable, name)) {
					keys.add(name);
				}
			});
		} else {
			list.forEach(obj -> {
				String fullName = this.getFullName(obj, this.getWithSchema().test(filename));
				String name = this.getName(obj);
				if (!CommonUtils.eq(fullName, name)) {
					keys.add(name);
				}
			});
		}
		keys.forEach(k -> {
			putProperty(fileProperties, k, null, mergeProperties);
		});
		propertiesMap.put(menuDefinition, mergeProperties);
		if (dryRun) {
			return;
		}
		if (getOutputDirectory() != null) {
			if (!getOutputDirectory().exists()) {
				getOutputDirectory().mkdirs();
			}
			if (file == null) {
				file = new File(this.getOutputDirectory(), filename + "." + this.getDictionaryFileType());
			}
		} else {
			if (getDirectory() != null) {
				if (!getDirectory().exists()) {
					getDirectory().mkdirs();
				}
				if (file == null) {
					file = new File(this.getDirectory(), filename + "." + this.getDictionaryFileType());
				}
			}
		}
		FileUtils.createParentDirectory(file);
		writeProperties(file, menuDefinition, mergeProperties);
	}

	private String getColumnNameWithTable(AbstractDbObject<?> obj) {
		Column column = (Column) obj;
		return column.getTableName() + "." + column.getName();
	}

	private void writeProperties(File file, MenuDefinition menuDefinition, Properties properties) throws Exception {
		DataFormat workbookFileType = DataFormat.parse(this.getDictionaryFileType());
		File outputFile;
		File tempFile;
		if (workbookFileType != null) {
			outputFile = new File(file.getParentFile(), FileUtils.getFileNameWithoutExtension(file.getAbsolutePath())
					+ "." + workbookFileType.getFileExtension());
		} else {
			outputFile = new File(file.getParentFile(),
					FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()) + "." + this.getDictionaryFileType());
		}
		if (workbookFileType != null) {
			tempFile = File.createTempFile(FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()),
					"." + workbookFileType.getFileExtension(), file.getParentFile());
		} else {
			tempFile = File.createTempFile(FileUtils.getFileNameWithoutExtension(file.getAbsolutePath()),
					"." + this.getDictionaryFileType(), file.getParentFile());
		}
		if ("properties".equalsIgnoreCase(this.getDictionaryFileType())) {
			try (OutputStream os = new FileOutputStream(tempFile)) {
				properties.store(os, menuDefinition + " dictionaries");
			}
		} else if ("xml".equalsIgnoreCase(this.getDictionaryFileType())) {
			try (OutputStream os = new FileOutputStream(tempFile)) {
				properties.storeToXML(os, menuDefinition + " dictionaries");
			}
		} else {
			writeOtherProperties(workbookFileType, tempFile, menuDefinition, properties);
		}
		FileUtils.remove(outputFile);
		tempFile.renameTo(outputFile);
		if (file != null) {
			outputFiles.add(tempFile);
		}
		if (!file.equals(outputFile)) {
			if (removeOriginalFile) {
				file.delete();
			}
		}
	}

	private void writeOtherProperties(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties) throws Exception {
		if (workbookFileType.isTextFile() && workbookFileType.isCsv()) {
			writeAsCsv(workbookFileType, file, menuDefinition, properties);
		} else if (workbookFileType.isWorkbook()) {
			writeAsWorkbook(workbookFileType, file, menuDefinition, properties);
		} else if (workbookFileType.isJson()) {
			writeAsJson(workbookFileType, file, menuDefinition, properties);
		} else if (workbookFileType.isYaml()) {
			writeAsYaml(workbookFileType, file, menuDefinition, properties);
		} else {
			throw new InvalidFileTypeException(file);
		}
	}

	private void writeAsCsv(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties) throws Exception {
		final List<String> headers = getHeaders(menuDefinition, properties);
		try (FileOutputStream fos = new FileOutputStream(file);
				OutputStreamWriter writer = new OutputStreamWriter(fos, getCsvEncoding());
				BufferedWriter bw = new BufferedWriter(writer);
				TextFileWriter csvWriter = workbookFileType.createCsvListWriter(bw)) {
			csvWriter.writeHeader(headers.toArray(new String[0]));
			final Map<String, String> duplicateCheck = CommonUtils.map();
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				final String[] values = createWriteData(properties, entry, headers, duplicateCheck);
				if (values == null) {
					continue;
				}
				csvWriter.writeRow(values);
			}
		}
	}

	private List<String> getHeaders(MenuDefinition menuDefinition, Properties properties) {
		final List<MenuDefinition> menuDefinitions = CommonUtils.list(menuDefinition.getNest());
		int maxNestLebel = getMaxNestLebel(properties);
		if (maxNestLebel != menuDefinition.getNestLevel()) {
			menuDefinitions.remove(0);
		}
		final List<String> headers = menuDefinitions.stream().map(c -> c.toString()).collect(Collectors.toList());
		for (String keyword : getKeywords()) {
			String name = SchemaUtils.getSingularName(keyword);
			headers.add(name);
		}
		return headers;
	}

	private String[] createWriteData(Properties properties, Map.Entry<Object, Object> entry, List<String> headers,
			Map<String, String> duplicateCheck) {
		String key = entry.getKey().toString();
		String cache = duplicateCheck.get(key);
		if (!CommonUtils.isEmpty(cache)) {
			return null;
		}
		String[] values = new String[headers.size()];
		String value = (String) entry.getValue();
		duplicateCheck.put(key, value);
		int pos = key.lastIndexOf('.');
		if (pos < 0) {
			throw new InvalidPropertyException(key, value);
		}
		String suffix = key.substring(pos + 1);
		String[] args = key.split("\\.");
		for (int i = 0; i < headers.size(); i++) {
			values[i] = "";
			String header = headers.get(i);
			if (i < headers.size() - 2) {
				values[i] = getHeaderValue(headers, i, args);
			} else {
				if (suffix.equalsIgnoreCase(header)) {
					values[i] = value;
				} else {
					String otherKey = key.substring(0, pos) + "." + header;
					Object otherValue = properties.getProperty(otherKey);
					if (otherValue != null) {
						values[i] = otherValue.toString();
					}
				}
			}
		}
		return values;
	}

	private void writeAsWorkbook(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties) throws IOException {
		final List<String> headers = getHeaders(menuDefinition, properties);
		try (FileOutputStream fos = new FileOutputStream(file)) {
			Workbook workbook = workbookFileType.createWorkbook();
			Sheet sheet = workbook.createSheet(menuDefinition.toString());
			int rowNo = 0;
			org.apache.poi.ss.usermodel.Row row = ExcelUtils.getOrCreateRow(sheet, rowNo++);
			int cellNo = 0;
			CellStyle cellStyle = ExcelCommandUtils.createCellStyleHeader(sheet);
			for (String header : headers) {
				ExcelCommandUtils.setCellValue(row, cellNo++, header, null, cellStyle);
			}
			final Map<String, String> duplicateCheck = CommonUtils.map();
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				final String[] values = createWriteData(properties, entry, headers, duplicateCheck);
				if (values == null) {
					continue;
				}
				row = ExcelUtils.getOrCreateRow(sheet, rowNo++);
				cellNo = 0;
				for (String val : values) {
					Cell cell = ExcelUtils.getOrCreateCell(row, cellNo++);
					if ("".equals(val)) {
						cell.setCellValue((String) null);
					} else {
						cell.setCellValue(val);
					}
				}
			}
			for (int i = 0; i < headers.size(); i++) {
				sheet.autoSizeColumn(i);
			}
			workbook.write(fos);
		}
	}

	private void writeAsJson(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties) throws IOException {
		writeAsText(workbookFileType, file, menuDefinition, properties, (map, bw) -> {
			String text = this.getJsonConverter().toJsonString(map);
			bw.write(text);
		});
	}

	private void writeAsYaml(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties) throws IOException {
		writeAsText(workbookFileType, file, menuDefinition, properties, (map, bw) -> {
			String text = this.getYamlConverter().toJsonString(map);
			System.out.println(text);
			bw.write(text);
		});
	}

	private void writeAsText(DataFormat workbookFileType, File file, MenuDefinition menuDefinition,
			Properties properties, IOEBiConsumer<Map<String, Object>, BufferedWriter> cons) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file);
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));) {
			Map<String, Object> map = CommonUtils.linkedMap();
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				String key = entry.getKey().toString();
				String value = (String) entry.getValue();
				int pos = key.lastIndexOf('.');
				String[] args = key.split("\\.");
				String suffix = key.substring(pos + 1);
				if (!SchemaProperties.DISPLAY_NAME.getLabel().equalsIgnoreCase(suffix)) {
					continue;
				}
				putValue(map, args, value);
			}
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				String key = entry.getKey().toString();
				String value = (String) entry.getValue();
				int pos = key.lastIndexOf('.');
				String[] args = key.split("\\.");
				String suffix = key.substring(pos + 1);
				if (SchemaProperties.DISPLAY_NAME.getLabel().equalsIgnoreCase(suffix)) {
					continue;
				}
				putValue(map, args, value);
			}
			cons.accept(map, bw);
		}
	}

	@FunctionalInterface
	public interface IOEBiConsumer<T, U> {

		/**
		 * Performs this operation on the given arguments.
		 *
		 * @param t the first input argument
		 * @param u the second input argument
		 */
		void accept(T t, U u) throws IOException;
	}

	@SuppressWarnings("unchecked")
	private void putValue(Map<String, Object> map, String[] args, String value) {
		if (args.length == 1 && getKeywordsMap().containsKey(args[0])) {
			return;
		}
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (i == args.length - 1) {
				map.put(arg, value);
			} else {
				Object obj = map.get(arg);
				if (obj == null) {
					Map<String, Object> child = CommonUtils.linkedMap();
					map.put(arg, child);
					map = child;
				} else {
					map = (Map<String, Object>) obj;
				}
			}

		}
	}

	private String getHeaderValue(List<String> headers, int i, String[] args) {
		int diff = headers.size() - 2 - (args.length - 1);
		if (i >= diff) {
			return args[i - diff];
		} else {
			return "";
		}
	}

	private int getMaxNestLebel(Properties properties) {
		int level = 0;
		for (Object key : properties.keySet()) {
			int current = key.toString().split("\\.").length;
			level = Math.max(level, current - 1);
		}
		return level;
	}

	private void putProperty(Properties fileProperties, String name, AbstractDbObject<?> obj,
			Properties mergeProperties) {
		for (String keyword : getKeywords()) {
			String key = name + "." + keyword;
			String value = fileProperties.getProperty(key);
			if (!CommonUtils.isEmpty(value)) {
				mergeProperties.put(key, value);
			} else {
				if (this.isOutputRemarksAsDisplayName()) {
					if (SchemaProperties.DISPLAY_NAME.getLabel().equals(keyword)) {
						if (obj instanceof RemarksProperty) {
							RemarksProperty<?> remarksProperty = (RemarksProperty<?>) obj;
							value = remarksProperty.getRemarks();
						}
					}
				}
				if (!CommonUtils.isEmpty(value)) {
					mergeProperties.put(key, value);
				} else {
					mergeProperties.put(key, "");
				}
			}
		}
	}

	static class StringComparator implements Comparator<String> {

		private Map<String, Integer> keywordsMap;

		StringComparator(Map<String, Integer> keywordsMap) {
			this.keywordsMap = keywordsMap;
		}

		@Override
		public int compare(String o1, String o2) {
			String[] split1 = o1.split("\\.");
			String[] split2 = o2.split("\\.");
			if (split1.length > split2.length) {
				return 1;
			} else if (split1.length < split2.length) {
				return -1;
			}
			int comp = compareWithoutLast(split1, split2);
			if (comp != 0) {
				return comp;
			}
			return compareLast(split1, split2);
		}

		private int compareWithoutLast(String[] split1, String[] split2) {
			for (int i = 0; i < split1.length - 1; i++) {
				int comp = split1[i].compareTo(split2[i]);
				if (comp != 0) {
					return comp;
				}
			}
			return 0;
		}

		private int compareLast(String[] split1, String[] split2) {
			String value1 = split1[split1.length - 1];
			String value2 = split2[split2.length - 1];
			Integer int1 = keywordsMap.get(value1);
			Integer int2 = keywordsMap.get(value2);
			if (int1 == null) {
				if (int2 == null) {
					return value1.compareTo(value2);
				} else {
					return 1;
				}
			} else {
				if (int2 == null) {
					return 0;
				} else {
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
