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

package com.sqlapp.data.db.command;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.DefaultSqlExecutor;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlExecutor;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;

/**
 * ファイル→DBコマンド
 * 
 * @author tatsuo satoh
 * 
 */
public abstract class AbstractFile2DataSourceCommand<T> extends
		AbstractSchemaDataSourceCommand {

	private File[] files = null;

	private SqlExecutor sqlExecutor = DefaultSqlExecutor.getInstance();

	private Options sqlOptions = new Options();

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
			try {
				final DbCommonObject<?> dbCommonObject = SchemaUtils
						.readXml(file);
				totalObjects.add(dbCommonObject);
			} catch (final IOException e) {
				this.getExceptionHandler().handle(e);
			} catch (final XMLStreamException e) {
				this.getExceptionHandler().handle(e);
			}
		}
		totalObjects = convertHandler.handle(totalObjects);
		Connection connection=null;
		try {
			connection = this.getConnection();
			final Dialect dialect=this.getDialect(connection);
			handle(totalObjects, connection, dialect);
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		}
	}

	protected void handle(final List<DbCommonObject<?>> totalObjects,
			final Connection connection, final Dialect dialect) throws Exception {
		final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry(dialect);
		sqlFactoryRegistry.setOption(this.getSqlOptions());
		List<T> list = getTarget(totalObjects, connection, dialect);
		list = filter(list);
		list = sort(list);
		handle(list, sqlFactoryRegistry, connection, dialect);
	}

	protected abstract List<T> getTarget(List<DbCommonObject<?>> totalObjects,
			Connection connection, Dialect dialect);

	protected List<T> filter(final List<T> list) {
		return list;
	}

	protected List<T> sort(final List<T> list) {
		return list;
	}

	protected void handle(final List<T> list, final SqlFactoryRegistry sqlFactoryRegistry,
			final Connection connection, final Dialect dialect) throws Exception {
		for (final T obj : list) {
			handle(obj, sqlFactoryRegistry, connection, dialect);
		}
	}

	protected abstract void handle(T obj, SqlFactoryRegistry sqlFactoryRegistry,
			Connection connection, Dialect dialect) throws Exception;

	/**
	 * @return the sqlExecutor
	 */
	public SqlExecutor getSqlExecutor() {
		return sqlExecutor;
	}

	/**
	 * @param sqlExecutor
	 *            the sqlExecutor to set
	 */
	public void setSqlExecutor(final SqlExecutor sqlExecutor) {
		this.sqlExecutor = sqlExecutor;
	}

	/**
	 * @return the files
	 */
	public File[] getFiles() {
		return files;
	}

	/**
	 * @param files
	 *            the files to set
	 */
	public void setFiles(final File... files) {
		this.files = files;
	}

	/**
	 * @return the sqlOption
	 */
	public Options getSqlOptions() {
		return sqlOptions;
	}

	/**
	 * @param sqlOptions
	 *            the sqlOptions to set
	 */
	public void setSqlOption(final Options sqlOptions) {
		this.sqlOptions = sqlOptions;
	}

}
