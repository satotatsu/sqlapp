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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.sql.DataSource;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.converter.TimestampConverter;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.jdbc.ConnectionHandler;
import com.sqlapp.jdbc.DataSourceConnectionHandler;
import com.sqlapp.util.OutputTextBuilder;

public abstract class AbstractDataSourceCommand extends AbstractCommand {

	private DataSource dataSource;

	private Connection connection;

	private Dialect dialect;

	private ConnectionHandler connectionHandler = null;

	private Converters converters=newConverters();
	
	protected Converters newConverters(){
		Converters converters=new Converters();
		TimestampConverter converter=converters.getConverter(Timestamp.class);
		converter.getZonedDateTimeConverter().setFormat("uuuu-MM-dd HH:mm:ss");
		return converters;
	}
	
	protected Connection getConnection() {
		if (this.connection!=null){
			return this.connection;
		}
		try {
			Connection connection = getConnectionHandler().getConnection();
			return connection;
		} catch (SQLException e) {
			return this.getExceptionHandler().handle(e);
		}
	}

	protected void releaseConnection(Connection connection) {
		if (this.connection!=null){
			return;
		}
		try {
			getConnectionHandler().releaseConnection(connection);
		} catch (SQLException e) {
			this.getExceptionHandler().handle(e);
		}
	}
	
	protected OutputTextBuilder createOutputTextBuilder(){
		OutputTextBuilder builder= new OutputTextBuilder();
		builder.setConverters(converters);
		return builder;
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource
	 *            the dataSource to set
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect() {
		if (dialect == null) {
			Connection connection = null;
			try {
				connection = this.getConnection();
				dialect = getDialect(connection);
			} finally {
				this.releaseConnection(connection);
			}
		}
		return dialect;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect(Connection connection) {
		try {
			connection = this.getConnection();
			return DialectResolver.getInstance().getDialect(connection);
		} finally {
			this.releaseConnection(connection);
		}
	}
	
	protected String getCurrentCatalogName(Connection connection) {
		return getDialect().getCatalogReader()
				.getCurrentCatalogName(connection);
	}

	protected String getCurrentSchemaName(Connection connection) {
		return getDialect().getCatalogReader().getSchemaReader()
				.getCurrentSchemaName(connection);
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the connectionHandler
	 */
	public ConnectionHandler getConnectionHandler() {
		if (this.connectionHandler==null){
			this.connectionHandler = new DataSourceConnectionHandler(dataSource);
		}
		return connectionHandler;
	}

	/**
	 * @param connectionHandler
	 *            the connectionHandler to set
	 */
	public void setConnectionHandler(ConnectionHandler connectionHandler) {
		this.connectionHandler = connectionHandler;
	}

	/**
	 * @param connection the connection to set
	 */
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

}
