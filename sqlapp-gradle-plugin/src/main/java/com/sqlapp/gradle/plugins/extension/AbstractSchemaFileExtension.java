/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins.extension;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;

import com.sqlapp.gradle.plugins.properties.CsvEncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.JsonConverterTaskProperty;
import com.sqlapp.gradle.plugins.properties.TomlConverterTaskProperty;
import com.sqlapp.gradle.plugins.properties.YamlConverterTaskProperty;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.TomlConverter;
import com.sqlapp.util.YamlConverter;

/**
 * Schema用のExtension
 */
public abstract class AbstractSchemaFileExtension extends AbstractDbExtension implements CsvEncodingTaskProperty,
		JsonConverterTaskProperty, TomlConverterTaskProperty, YamlConverterTaskProperty {
	@Inject
	protected AbstractSchemaFileExtension(Project project) {
		super(project);
	}

	private JsonConverter jsonConverter;

	@Internal
	@Override
	public JsonConverter getJsonConverter() {
		return this.jsonConverter;
	}

	@Override
	public void setJsonConverter(JsonConverter jsonConverter) {
		this.jsonConverter = jsonConverter;
	}

	private YamlConverter yamlConverter;

	@Internal
	@Override
	public YamlConverter getYamlConverter() {
		return this.yamlConverter;
	}

	@Override
	public void setYamlConverter(YamlConverter yamlConverter) {
		this.yamlConverter = yamlConverter;
	}

	private TomlConverter tomlConverter;

	@Internal
	@Override
	public TomlConverter getTomlConverter() {
		return this.tomlConverter;
	}

	@Override
	public void setTomlConverter(TomlConverter tomlConverter) {
		this.tomlConverter = tomlConverter;
	}

}
