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

package com.sqlapp.gradle.plugins;

import java.util.function.Function;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.html.GenerateHtmlDocsCommand;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.gradle.plugins.extension.RenderOptionExtension;
import com.sqlapp.gradle.plugins.properties.DictionaryFileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.DictionaryFileTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.DirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.ForeignKeyDefinitionDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.JsonConverterTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;
import com.sqlapp.gradle.plugins.properties.TomlConverterTaskProperty;
import com.sqlapp.gradle.plugins.properties.UseSchemaNameDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.YamlConverterTaskProperty;
import com.sqlapp.util.JsonConverter;
import com.sqlapp.util.YamlConverter;

@DisableCachingByDefault
public abstract class GenerateHtmlDocsTask extends AbstractTask<GenerateHtmlDocsCommand, Void>
		implements FileDirectoryTaskProperty, DirectoryTaskProperty, OutputDirectoryTaskProperty,
		PlaceholderTaskProperty, UseSchemaNameDirectoryTaskProperty, DictionaryFileDirectoryTaskProperty,
		DictionaryFileTypeTaskProperty, TargetFileTaskProperty, ForeignKeyDefinitionDirectoryTaskProperty,
		JsonConverterTaskProperty, TomlConverterTaskProperty, YamlConverterTaskProperty {
	@Inject
	public GenerateHtmlDocsTask(ObjectFactory objectFactory) {
		super(objectFactory);
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

	@Override
	protected GenerateHtmlDocsCommand createCommand() {
		return new GenerateHtmlDocsCommand();
	}

	@Input
	@Optional
	public abstract Property<RenderOptionExtension> getRenderOptions();

	@Input
	@Optional
	public abstract Property<Boolean> getMultiThread();

	/** Virtual foreign Key definitions */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	@Optional
	public abstract DirectoryProperty getForeignKeyDefinitionDirectory();

	private Function<ForeignKeyConstraint, String> virtualForeignKeyLabel;

	/** virtualForeignKeyLabel */
	@Input
	@Optional
	public Function<ForeignKeyConstraint, String> getVirtualForeignKeyLabel() {
		return virtualForeignKeyLabel;
	}

	public void setVirtualForeignKeyLabel(Function<ForeignKeyConstraint, String> value) {
		virtualForeignKeyLabel = value;
	}

	protected void beforeRun(GenerateHtmlDocsCommand command) {
		if (getRenderOptions().isPresent()) {
			getRenderOptions().get().setRenderOption(command.getRenderOptions());
		}
		if (getMultiThread().isPresent()) {
			command.setMultiThread(getMultiThread().get());
		}
		if (getForeignKeyDefinitionDirectory().isPresent()) {
			command.setForeignKeyDefinitionDirectory(getForeignKeyDefinitionDirectory().get().getAsFile());
		}
		if (getJsonConverter() != null) {
			command.setJsonConverter(getJsonConverter());
		}
		if (getYamlConverter() != null) {
			command.setYamlConverter(getYamlConverter());
		}
		if (getVirtualForeignKeyLabel() != null) {
			command.setVirtualForeignKeyLabel(getVirtualForeignKeyLabel());
		}
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}
}
