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

package com.sqlapp.data.db.command.generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.config.TableGeneratorConfig;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.properties.ForeignKeyDefinitionDirectoryProperty;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.RowAmplificationFactorProperty;
import com.sqlapp.data.db.command.properties.SqlTypeProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaTypeHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VirtualForeignKeyLoader;
import com.sqlapp.data.schemas.function.ExceptionSupplier;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class GenerateDataConfigCommand extends AbstractTableCommand implements SqlTypeProperty, OutputDirectoryProperty,
		RowAmplificationFactorProperty, ForeignKeyDefinitionDirectoryProperty {
	/**
	 * SQL Type
	 */
	private SqlType sqlType = SqlType.INSERT;

	/** file input */
	private File inputFile;
	/** file directory */
	private File outputDirectory = new File("./");
	/** fileType */
	private ConfigFileType fileType = ConfigFileType.EXCEL;
	/** locale */
	private Locale locale = Locale.getDefault();
	/** Virtual foreign Key definitions */
	private File foreignKeyDefinitionDirectory = null;
	/** TableDataGeneratorConfigFactory */
	private TableGeneratorConfigFactory generatorConfigFactory = new TableGeneratorConfigFactory();

	private VirtualForeignKeyLoader createVirtualForeignKeyLoader() {
		VirtualForeignKeyLoader loader = new VirtualForeignKeyLoader();
		return loader;
	}

	@Override
	protected void doRun() {
		final List<File> files = CommonUtils.list();
		if (inputFile != null) {
			final List<Table> tables = readFromFile(inputFile);
			final Dialect dialect = getDaialect(tables);
			execute(() -> {
				outputFiles(files, () -> dialect, () -> tables);
			});
		} else {
			execute(getDataSource(), connection -> {
				final Dialect dialect = this.getDialect(connection);
				outputFiles(files, () -> dialect, () -> getTables(dialect, connection));
			});
		}
		doRunAfter(files);
	}

	private Dialect getDaialect(List<Table> tables) {
		for (Table table : tables) {
			return table.getDialect();
		}
		return null;
	}

	private List<Table> readFromFile(File inputFile) {
		try {
			DbCommonObject<?> obj = SchemaUtils.readXml(inputFile);
			VirtualForeignKeyLoader loader = createVirtualForeignKeyLoader();
			SchemaTypeHandler handher = new SchemaTypeHandler();
			handher.setCatalogConsumer(catalog -> {
				loader.load(catalog, getForeignKeyDefinitionDirectory());
			});
			handher.setSchemaConsumer(schema -> {
				loader.load(schema, getForeignKeyDefinitionDirectory());
			});
			handher.setTablesConsumer(tables -> {
				loader.loadTables(tables, getForeignKeyDefinitionDirectory());
			});
			handher.apply(obj);
			return SchemaUtils.toTables(obj);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void outputFiles(List<File> files, Supplier<Dialect> dialectSuppier,
			ExceptionSupplier<List<Table>> tableListSuppier) throws FileNotFoundException, IOException {
		final Dialect dialect = dialectSuppier.get();
		List<Table> tables;
		try {
			tables = tableListSuppier.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (tables.isEmpty()) {
			info("No table found. includeSchemas=" + Arrays.toString(this.getIncludeSchemas()) + ", excludeSchemas="
					+ Arrays.toString(this.getExcludeSchemas()) + ", includeTables="
					+ Arrays.toString(this.getIncludeTables()) + ", excludeTables="
					+ Arrays.toString(this.getExcludeTables()));
			return;
		}
		if (getForeignKeyDefinitionDirectory() != null) {
			VirtualForeignKeyLoader loader = createVirtualForeignKeyLoader();
			loader.loadTables(tables, getForeignKeyDefinitionDirectory());
		}
		File dir = this.getOutputDirectory();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		for (Table table : tables) {
			files.add(writeFile(table, dir, dialect));
		}
	}

	protected void doRunAfter(List<File> files) {

	}

	private File writeFile(Table table, File dir, Dialect dialect) throws FileNotFoundException, IOException {
		final TableGeneratorConfig config = this.getGeneratorConfigFactory().createDefault(table, dialect,
				this.getTableOptions(), this.getSqlType());
		config.setFileType(fileType);
		File file = this.getGeneratorConfigFactory().writeFile(dir, locale, config);
		return file;
	}

	@Override
	public Function<Table, Integer> getRowAmplificationFactor() {
		return this.getGeneratorConfigFactory().getRowAmplificationFactor();
	}

	@Override
	public void setRowAmplificationFactor(Function<Table, Integer> value) {
		this.getGeneratorConfigFactory().setRowAmplificationFactor(value);
	}

}
