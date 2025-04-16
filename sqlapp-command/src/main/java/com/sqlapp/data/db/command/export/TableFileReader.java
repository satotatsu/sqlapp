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
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.JsonConverterProperty;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.data.db.command.properties.PropertyUtils;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
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

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableFileReader implements PlaceholderProperty, FilesProperty, CsvEncodingProperty, JsonConverterProperty,
		UseSchemaNameDirectoryProperty {
	/**
	 * data file Directory
	 */
	private File directory = null;
	/**
	 * data file
	 */
	private File[] files = null;

	private boolean useSchemaNameDirectory = false;

	private String csvEncoding = Charset.defaultCharset().toString();

	private int csvSkipHeaderRowsSize = 1;

	private JsonConverter jsonConverter = createJsonConverter();

	/** file directory */
	private File fileDirectory = null;
	/** file filter */
	private Predicate<File> fileFilter = f -> true;

	private String placeholderPrefix = "${";

	private String placeholderSuffix = "}";

	private boolean placeholders = false;

	private Map<String, Object> context = CommonUtils.linkedMap();

	public TableFileReader() {
	}

	public List<TableFilesPair> getTableFilesPairs(final Catalog catalog) {
		final Set<String> schemaNames = CommonUtils.lowerSet();
		if (this.getFiles() != null) {
			// ファイルが指定されている場合はファイルを優先
			final List<TableFilesPair> tfs = CommonUtils.list();
			final Map<String, List<File>> fileListMap = CommonUtils.map();
			for (final File file : this.getFiles()) {
				if (!isTargetFile(file)) {
					continue;
				}
				final String name = FileUtils.getFileNameWithoutExtension(file);
				List<File> list = fileListMap.get(name);
				if (list == null) {
					list = CommonUtils.list();
					fileListMap.put(name, list);
				}
				list.add(file);
			}
			catalog.getSchemas().forEach(s -> {
				s.getTables().forEach(t -> {
					List<File> list = fileListMap.get(t.getName());
					if (!CommonUtils.isEmpty(list)) {
						final TableFilesPair tf = new TableFilesPair(t, list);
						tfs.add(tf);
					}
				});
			});
			return tfs;
		}
		final List<TableFilesPair> tableFilesPairs;
		if (isUseSchemaNameDirectory()) {
			tableFilesPairs = CommonUtils.list();
			final File[] directories = getDirectory().listFiles(c -> c.isDirectory());
			if (directories != null) {
				for (final File directory : directories) {
					final String name = directory.getName();
					schemaNames.add(name);
					final Schema schema = catalog.getSchemas().get(name);
					if (schema != null) {
						tableFilesPairs.addAll(getTableFilesPairs(directory, schema));
					}
				}
			}
		} else {
			tableFilesPairs = getTableFilesPairs(getDirectory(), catalog.getSchemas());
		}
		return tableFilesPairs;
	}

	public void setFiles(final List<TableFilesPair> tfs)
			throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException {
		for (final TableFilesPair tf : tfs) {
			readFiles(tf.getTable(), tf.getFiles());
		}
	}

	private boolean isTargetFile(File file) {
		if (!file.isFile()) {
			return false;
		}
		if (!this.getFileFilter().test(file)) {
			return false;
		}
		if (WorkbookFileType.parse(file) == null) {
			return false;
		}
		return true;
	}

	public static class TableFilesPair {
		TableFilesPair(final Table table, final List<File> files) {
			this.table = table;
			this.synonym = null;
			this.name = table.getName();
			this.files = files;
		}

		TableFilesPair(final Table table, final File... files) {
			this.table = table;
			this.synonym = null;
			this.name = table.getName();
			this.files = CommonUtils.list(files);
		}

		TableFilesPair(final Table table) {
			this.table = table;
			this.synonym = null;
			this.name = table.getName();
			this.files = CommonUtils.list();
		}

		TableFilesPair(final Synonym synonym) {
			this.synonym = synonym;
			this.table = synonym.rootSynonym().getTable();
			this.name = synonym.getName();
			this.files = CommonUtils.list();
		}

		private final Table table;
		private final Synonym synonym;
		private final List<File> files;
		private final String name;

		/**
		 * @return the synonym
		 */
		public Synonym getSynonym() {
			return synonym;
		}

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

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
		public String toString() {
			final StringBuilder builder = new StringBuilder();
			builder.append("[");
			builder.append(table.getName());
			builder.append(", ");
			builder.append("files=");
			builder.append(files);
			builder.append("]");
			return builder.toString();
		}
	}

	private List<TableFilesPair> getTableFilesPairs(final File directory, final SchemaCollection schemas) {
		File[] files = null;
		if (directory != null && directory.exists()) {
			files = directory.listFiles();
		}
		final List<TableFilesPair> result = getTableFilePairWithFile((name) -> getTableFilesPair(schemas, name), files);
		return result;
	}

	private TableFilesPair getTableFilesPair(final SchemaCollection schemas, String name) {
		for (Schema schema : schemas) {
			TableFilesPair pair = getTableFilesPair(schema, name);
			if (pair != null) {
				return pair;
			}
		}
		return null;
	}

	private TableFilesPair getTableFilesPair(final Schema schema, String name) {
		final Table table = schema.getTables().get(name);
		if (table != null) {
			final TableFilesPair pair = new TableFilesPair(table);
			return pair;
		}
		final Synonym synonym = schema.getSynonyms().get(name);
		if (synonym != null) {
			final TableFilesPair pair = new TableFilesPair(synonym);
			return pair;
		}
		return null;
	}

	private List<TableFilesPair> getTableFilesPairs(final File directory, final Schema schema) {
		return getTableFilePairWithFile((name) -> getTableFilesPair(schema, name), directory.listFiles());
	}

	private List<TableFilesPair> getTableFilePairWithFile(final Function<String, TableFilesPair> func,
			final File... files) {
		final List<TableFilesPair> result = CommonUtils.list();
		if (files == null) {
			return result;
		}
		final List<File> fs = Arrays.stream(files).filter(f -> isTargetFile(f)).collect(Collectors.toList());
		final Map<String, List<File>> fileListMap = CommonUtils.map();
		for (final File file : fs) {
			final String name = FileUtils.getFileNameWithoutExtension(file);
			List<File> list = fileListMap.get(name);
			if (list == null) {
				list = CommonUtils.list();
				fileListMap.put(name, list);
			}
			list.add(file);
		}
		fileListMap.forEach((name, list) -> {
			final TableFilesPair pair = func.apply(name);
			if (pair != null) {
				pair.getFiles().addAll(list);
				result.add(pair);
			}
		});
		return result;
	}

	private SqlConverter getSqlConverter() {
		final SqlConverter sqlConverter = new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}

	private void readFiles(final Table table, final List<File> files)
			throws EncryptedDocumentException, InvalidFormatException, IOException, XMLStreamException {
		final List<RowIteratorHandler> handlers = files.stream().map(file -> {
			final WorkbookFileType workbookFileType = WorkbookFileType.parse(file);
			if (workbookFileType.isTextFile()) {
				if (workbookFileType.isCsv()) {
					return new CsvRowIteratorHandler(file, getCsvEncoding(), getCsvSkipHeaderRowsSize(),
							getRowValueConverter());
				} else if (workbookFileType.isXml()) {
					return new XmlRowIteratorHandler(file, getRowValueConverter());
				} else {
					return new JsonRowIteratorHandler(file, this.getJsonConverter(), getRowValueConverter());
				}
			} else {
				return new ExcelRowIteratorHandler(file, getRowValueConverter());
			}
		}).collect(Collectors.toList());
		if (!handlers.isEmpty()) {
			table.setRowIteratorHandler(new CombinedRowIteratorHandler(handlers));
		}
	}

	private RowValueConverter getRowValueConverter() {
		final SqlConverter sqlConverter = getSqlConverter();
		final ParametersContext context = new ParametersContext();
		context.putAll(this.getContext());
		return (r, c, v) -> {
			Object val;
			try {
				val = sqlConverter.getExpressionConverter().convert(v, context);
			} catch (final IOException e) {
				throw new InvalidValueException(r, c, v, e);
			}
			return val;
		};
	}

	@Override
	public void setFiles(File... obj) {
		this.files = PropertyUtils.convertArray(obj);
	}

}
