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

import org.gradle.api.Action;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.generator.util.CachedMvelEvaluatorUtils;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.DmlBatchSizeTaskProperty;
import com.sqlapp.gradle.plugins.properties.FetchSizeTaskProperty;
import com.sqlapp.gradle.plugins.properties.ForeignKeyDefinitionDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.GeneratorConfigFactoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentCatalogTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentSchemaTaskProperty;
import com.sqlapp.gradle.plugins.properties.QueryCommitIntervalTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionsTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.UseSchemaNameDirectoryTaskProperty;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

@DisableCachingByDefault
public abstract class GenerateDataTask extends AbstractDirectoryTask<GenerateDataInsertCommand>
		implements DataSourceTaskProperty, TableOptionsTaskProperty, QueryCommitIntervalTaskProperty,
		SchemaTargetTaskProperty, TableTargetTaskProperty, OnlyCurrentCatalogTaskProperty,
		OnlyCurrentSchemaTaskProperty, UseSchemaNameDirectoryTaskProperty, GeneratorConfigFactoryTaskProperty,
		ForeignKeyDefinitionDirectoryTaskProperty, FetchSizeTaskProperty, DmlBatchSizeTaskProperty {

	public void call(Action<GenerateDataTask> cons) {
		cons.execute(this);
	}

	private TableOptions tableOptions;

	@Internal
	public TableOptions getTableOptions() {
		return this.tableOptions;
	}

	public void setTableOptions(TableOptions tableOptions) {
		this.tableOptions = tableOptions;
	}

	private CachedMvelEvaluator evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();

	@Internal
	public CachedMvelEvaluator getEvaluator() {
		return this.evaluator;
	}

	public void setEvaluator(CachedMvelEvaluator evaluator) {
		this.evaluator = evaluator;
	}

	public void evaluator(Action<CachedMvelEvaluator> cons) {
		cons.execute(getEvaluator());
	}

	private TableGeneratorConfigFactory generatorConfigFactory = new TableGeneratorConfigFactory();

	@Internal
	@Override
	public TableGeneratorConfigFactory getGeneratorConfigFactory() {
		return this.generatorConfigFactory;
	}

	@Override
	public void setGeneratorConfigFactory(TableGeneratorConfigFactory generatorConfigFactory) {
		this.generatorConfigFactory = generatorConfigFactory;
	}

	@Override
	protected void beforeRun(GenerateDataInsertCommand command) {
		super.beforeRun(command);
		if (getTableOptions() != null) {
			command.setTableOptions(getTableOptions());
		}
		if (getEvaluator() != null) {
			command.setEvaluator(getEvaluator());
		}
		if (getGeneratorConfigFactory() != null) {
			command.setGeneratorConfigFactory(this.getGeneratorConfigFactory());
		}
	}

	@Override
	protected GenerateDataInsertCommand createCommand() {
		return new GenerateDataInsertCommand();
	}
}