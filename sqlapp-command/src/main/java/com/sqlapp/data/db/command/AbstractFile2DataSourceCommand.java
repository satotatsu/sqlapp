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

package com.sqlapp.data.db.command;

import java.io.File;
import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.data.db.command.properties.SchemaOptionProperty;
import com.sqlapp.data.db.command.properties.SqlExecutorProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.DefaultSqlExecutor;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * ファイル→DBコマンド
 * 
 * @author tatsuo satoh
 * 
 */
@Getter
@Setter
public abstract class AbstractFile2DataSourceCommand<T> extends AbstractSchemaDataSourceCommand
		implements SchemaOptionProperty, SqlExecutorProperty, FilesProperty {

	private File[] files = null;

	private SqlExecutor sqlExecutor = DefaultSqlExecutor.getInstance();

	private Options schemaOptions = new Options();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sqlapp.data.db.command.AbstractCommand#doRun()
	 */
	@Override
	protected void doRun() {
		List<DbCommonObject<?>> totalObjects = CommonUtils.list();
		final ConvertHandler convertHandler = getConvertHandler();
		for (final File file : getFiles()) {
			execute(() -> {
				final DbCommonObject<?> dbCommonObject = SchemaUtils.readXml(file);
				totalObjects.add(dbCommonObject);
			});
		}
		final List<DbCommonObject<?>> convertedTotalObjects = convertHandler.handle(totalObjects);
		execute(getDataSource(), connection -> {
			final Dialect dialect = this.getDialect(connection);
			handle(convertedTotalObjects, connection, dialect);
		});
	}

	protected void handle(final List<DbCommonObject<?>> totalObjects, final Connection connection,
			final Dialect dialect) throws Exception {
		final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry(dialect);
		sqlFactoryRegistry.setOption(this.getSchemaOptions());
		List<T> list = getTarget(totalObjects, connection, dialect);
		list = filter(list);
		list = sort(list);
		handle(list, sqlFactoryRegistry, connection, dialect);
	}

	protected abstract List<T> getTarget(List<DbCommonObject<?>> totalObjects, Connection connection, Dialect dialect);

	protected List<T> filter(final List<T> list) {
		return list;
	}

	protected List<T> sort(final List<T> list) {
		return list;
	}

	protected void handle(final List<T> list, final SqlFactoryRegistry sqlFactoryRegistry, final Connection connection,
			final Dialect dialect) throws Exception {
		for (final T obj : list) {
			handle(obj, sqlFactoryRegistry, connection, dialect);
		}
	}

	protected abstract void handle(T obj, SqlFactoryRegistry sqlFactoryRegistry, Connection connection, Dialect dialect)
			throws Exception;

	@Override
	public void setFiles(File... obj) {
		this.files = obj;
	}

}
