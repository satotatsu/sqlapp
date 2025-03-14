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
import java.io.FileOutputStream;
import java.sql.Connection;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;

import com.sqlapp.data.db.command.AbstractDataSourceCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.CatalogReader;
import com.sqlapp.data.db.metadata.TableReader;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType;
import com.sqlapp.util.FileUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * Generate Data and Insert Command
 */
@Getter
@Setter
public class OutputGenerateDataTemplateCommand extends AbstractDataSourceCommand {
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
	private SqlType sqlType = SqlType.INSERT_ROW;

	/** file directory */
	private File outputDirectory = new File("./");

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
				String path = FileUtils.combinePath(this.getOutputDirectory().getAbsoluteFile(),
						table.getName() + ".xlsx");
				File file = new File(path);
				try (Workbook wb = WorkbookFileType.EXCEL2007.createWorkbook()) {
					GeneratorSettingWorkbook.Table.writeSheet(table, wb);
					GeneratorSettingWorkbook.Column.writeSheet(table, wb);
					GeneratorSettingWorkbook.QueryDefinition.writeSheet(table, wb);
					try (FileOutputStream os = new FileOutputStream(file);
							BufferedOutputStream bs = new BufferedOutputStream(os)) {
						wb.write(bs);
						bs.flush();
					}
				}
			}
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		}
	}
}
