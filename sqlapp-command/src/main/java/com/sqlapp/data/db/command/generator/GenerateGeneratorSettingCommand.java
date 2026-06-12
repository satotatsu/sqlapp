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

import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.command.properties.OutputDirectoryProperty;
import com.sqlapp.data.db.command.properties.SqlTypeProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class GenerateGeneratorSettingCommand extends AbstractTableCommand
		implements SqlTypeProperty, OutputDirectoryProperty {
	/**
	 * SQL Type
	 */
	private SqlType sqlType = SqlType.INSERT;

	/** file directory */
	private File outputDirectory = new File("./");
	/** fileType */
	private GeneratorSettingFileType fileType = GeneratorSettingFileType.EXCEL;
	/** locale */
	private Locale locale = Locale.getDefault();

	/** TableDataGeneratorSettingFactory */
	private TableGeneratorSettingFactory generatorSettingFactory = new TableGeneratorSettingFactory();

	@Override
	protected void doRun() {
		List<File> files = CommonUtils.list();
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			final List<Table> tables = getTables(connection, dialect);
			if (tables.isEmpty()) {
				info("No table found. includeSchemas=" + Arrays.toString(this.getIncludeSchemas()) + ", excludeSchemas="
						+ Arrays.toString(this.getExcludeSchemas()) + ", includeTables="
						+ Arrays.toString(this.getIncludeTables()) + ", excludeTables="
						+ Arrays.toString(this.getExcludeTables()));
				return;
			}
			File dir = this.getOutputDirectory();
			if (!dir.exists()) {
				dir.mkdirs();
			}
			for (Table table : tables) {
				files.add(writeFile(table, dir, dialect));
			}
		});
		doRunAfter(files);
	}

	protected void doRunAfter(List<File> files) {

	}

	private File writeFile(Table table, File dir, Dialect dialect) throws FileNotFoundException, IOException {
		final TableGeneratorSetting setting = this.getGeneratorSettingFactory().createDefault(table, dialect,
				this.getTableOptions(), this.getSqlType());
		setting.setFileType(fileType);
		File file = this.getGeneratorSettingFactory().writeFile(dir, locale, setting);
		return file;
	}

}
