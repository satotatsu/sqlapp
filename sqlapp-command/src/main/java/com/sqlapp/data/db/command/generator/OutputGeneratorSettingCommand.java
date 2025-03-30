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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.db.command.generator.setting.TableGeneratorSetting;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.JsonConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class OutputGeneratorSettingCommand extends AbstractDataSourceCommand {
	/**
	 * schema name
	 */
	private String schemaName;
	/**
	 * table name
	 */
	private String tableName;
	/**
	 * SQL Type
	 */
	private SqlType sqlType = SqlType.INSERT;

	/** file directory */
	private File outputDirectory = new File("./");
	/** table option */
	private TableOptions tableOptions = new TableOptions();
	/** fileType */
	private GeneratorSettingFileType fileType = GeneratorSettingFileType.EXCEL2007;

	/** TableDataGeneratorSettingFactory */
	private TableGeneratorSettingFactory generatorSettingFactory = new TableGeneratorSettingFactory();

	@Override
	protected void doRun() {
		Connection connection = null;
		try {
			connection = this.getConnection();
			final Dialect dialect = this.getDialect(connection);
			CatalogReader catalogReader = dialect.getCatalogReader();
			TableReader tableReader = catalogReader.getSchemaReader().getTableReader();
			tableReader.setSchemaName(this.getSchemaName());
			tableReader.setObjectName(this.getTableName());
			List<Table> tableList = tableReader.getAllFull(connection);
			if (tableList.isEmpty()) {
				throw new TableNotFoundException(
						"schemaName=" + this.getSchemaName() + ", tableName=" + getTableName());
			}
			if (tableList.isEmpty()) {
				throw new MultiTableFoundException("schemaName=" + this.getSchemaName() + ", tableName="
						+ getTableName() + ", tableSize=" + tableList.size());
			}
			File dir = this.getOutputDirectory();
			if (!dir.exists()) {
				dir.mkdirs();
			}
			for (Table table : tableList) {
				writeFile(table, dir, dialect);
			}
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		} finally {
			releaseConnection(connection);
		}
	}

	private void writeFile(Table table, File dir, Dialect dialect) throws FileNotFoundException, IOException {
		final TableGeneratorSetting setting = this.getGeneratorSettingFactory().createDefault(table, dialect,
				this.getTableOptions(), this.getSqlType());
		switch (this.getFileType()) {
		case JSON:
		case YAML:
			writeTextFile(setting, dir);
			break;
		default:
			writeFileWorkbook(setting, dir);
		}
	}

	private void writeTextFile(TableGeneratorSetting setting, File dir) throws FileNotFoundException, IOException {
		JsonConverter jsonConverter = this.getFileType().getWorkbookFileType().createJsonConverter();
		jsonConverter.setIndentOutput(true);
		String text = jsonConverter.toJsonString(setting);
		FileUtils.write(
				new File(dir, setting.getName() + "." + this.getFileType().getWorkbookFileType().getFileExtension()),
				text, Charset.forName("UTF-8"));
	}

	private void writeFileWorkbook(TableGeneratorSetting setting, File dir) throws FileNotFoundException, IOException {
		try (Workbook wb = this.getFileType().getWorkbookFileType().createWorkbook()) {
			GeneratorSettingWorkbook.Table.writeSheet(setting, wb);
			GeneratorSettingWorkbook.Column.writeSheet(setting, wb);
			GeneratorSettingWorkbook.Query.writeSheet(setting, wb);
			File file = new File(dir, setting.getName() + ".xlsx");
			try (FileOutputStream os = new FileOutputStream(file);
					BufferedOutputStream bs = new BufferedOutputStream(os)) {
				wb.write(bs);
				bs.flush();
			}
		}
	}

}
