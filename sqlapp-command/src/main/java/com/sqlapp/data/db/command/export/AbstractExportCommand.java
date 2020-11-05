/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.command.export;

import java.io.File;
import java.nio.charset.Charset;

import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.YamlConverter;

/**
 * Exportコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractExportCommand extends AbstractTableCommand {
	/**
	 * Output Direcroty
	 */
	private File directory=new File(".");
	
	private boolean useSchemaNameDirectory=false;

	private String csvEncoding=Charset.defaultCharset().toString();

	private JsonConverter jsonConverter=createJsonConverter();

	private YamlConverter yamlConverter=createYamlConverter();
	
	private TableOptions tableOptions=new TableOptions();
	
	public AbstractExportCommand(){
		getTableOptions().setDmlBatchSize(t->Integer.MAX_VALUE);
	}
	
	/**
	 * @return the jsonConverter
	 */
	public JsonConverter getJsonConverter() {
		return jsonConverter;
	}
	
	/**
	 * @param jsonConverter the jsonConverter to set
	 */
	public void setJsonConverter(JsonConverter jsonConverter) {
		this.jsonConverter = jsonConverter;
	}

	public YamlConverter getYamlConverter() {
		return yamlConverter;
	}

	public void setYamlConverter(YamlConverter yamlConverter) {
		this.yamlConverter = yamlConverter;
	}

	/**
	 * @return the directory
	 */
	public File getDirectory() {
		if (directory==null) {
			this.directory=new File("./");
		}
		return directory;
	}

	/**
	 * @param directory the directory to set
	 */
	public void setDirectory(File directory) {
		this.directory = directory;
	}

	/**
	 * @return the useSchemaNameDirectory
	 */
	public boolean isUseSchemaNameDirectory() {
		return useSchemaNameDirectory;
	}

	/**
	 * @param useSchemaNameDirectory the useSchemaNameDirectory to set
	 */
	public void setUseSchemaNameDirectory(boolean useSchemaNameDirectory) {
		this.useSchemaNameDirectory = useSchemaNameDirectory;
	}

	/**
	 * @return the csvEncoding
	 */
	public String getCsvEncoding() {
		return csvEncoding;
	}

	/**
	 * @param csvEncoding the csvEncoding to set
	 */
	public void setCsvEncoding(String csvEncoding) {
		this.csvEncoding = csvEncoding;
	}


	/**
	 * @return the tableOptions
	 */
	public TableOptions getTableOptions() {
		return tableOptions;
	}


	/**
	 * @param tableOptions the tableOptions to set
	 */
	public void setTableOptions(TableOptions tableOptions) {
		this.tableOptions = tableOptions;
	}



}
