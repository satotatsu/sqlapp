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

	private final Converters converters=newConverters();
	
	protected Converters newConverters(){
		final Converters converters=new Converters();
		final TimestampConverter converter=converters.getConverter(Timestamp.class);
		converter.getZonedDateTimeConverter().setFormat("uuuu-MM-dd HH:mm:ss");
		return converters;
	}
	
	protected Connection getConnection() {
		if (this.connection!=null){
			return this.connection;
		}
		try {
			final Connection connection = getConnectionHandler().getConnection();
			return connection;
		} catch (final SQLException e) {
			return this.getExceptionHandler().handle(e);
		}
	}

	protected void releaseConnection(final Connection connection) {
		if (this.connection!=null){
			return;
		}
		if (connection==null){
			return;
		}
		try {
			getConnectionHandler().releaseConnection(connection);
		} catch (final SQLException e) {
			this.getExceptionHandler().handle(e);
		}
	}
	
	protected void rollback(final Connection connection){
		if (connection==null){
			return;
		}
		try {
			connection.rollback();
		} catch (final SQLException e) {
		}
	}
	
	protected OutputTextBuilder createOutputTextBuilder(){
		final OutputTextBuilder builder= new OutputTextBuilder();
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
	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect(Connection connection) {
		this.dialect = DialectResolver.getInstance().getDialect(connection);
		return this.dialect;
	}
	
	protected String getCurrentCatalogName(final Connection connection, final Dialect dialect) {
		return dialect.getCatalogReader()
				.getCurrentCatalogName(connection);
	}

	protected String getCurrentSchemaName(final Connection connection, final Dialect dialect) {
		return dialect.getCatalogReader().getSchemaReader()
				.getCurrentSchemaName(connection);
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	public void setDialect(final Dialect dialect) {
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
	public void setConnectionHandler(final ConnectionHandler connectionHandler) {
		this.connectionHandler = connectionHandler;
	}

	/**
	 * @param connection the connection to set
	 */
	public void setConnection(final Connection connection) {
		this.connection = connection;
	}

}
