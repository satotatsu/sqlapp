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

import java.nio.charset.Charset;

import com.sqlapp.data.db.command.AbstractTableCommand;
import com.sqlapp.data.db.command.properties.CsvEncodingProperty;
import com.sqlapp.data.db.command.properties.JsonConverterProperty;
import com.sqlapp.data.db.command.properties.TableOptionProperty;
import com.sqlapp.data.db.command.properties.TomlConverterProperty;
import com.sqlapp.data.db.command.properties.UseSchemaNameDirectoryProperty;
import com.sqlapp.data.db.command.properties.YamlConverterProperty;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

import lombok.Getter;
import lombok.Setter;

/**
 * Exportコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public abstract class AbstractExportCommand extends AbstractTableCommand
		implements TableOptionProperty, CsvEncodingProperty, JsonConverterProperty, TomlConverterProperty,
		YamlConverterProperty, UseSchemaNameDirectoryProperty {

	private boolean useSchemaNameDirectory = false;

	private String csvEncoding = Charset.defaultCharset().toString();

	private JsonConverter jsonConverter = createJsonConverter();

	private TomlConverter tomlConverter = createTomlConverter();

	private YamlConverter yamlConverter = createYamlConverter();

	public YamlConverter getYamlConverter() {
		return this.yamlConverter;
	}

	private TableOptions tableOptions = new TableOptions();

	public AbstractExportCommand() {
		getTableOptions().setDmlBatchSize(t -> Integer.MAX_VALUE);
	}

}
