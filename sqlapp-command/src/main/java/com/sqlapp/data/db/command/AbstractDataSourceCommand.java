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
import com.sqlapp.jdbc.ReleaseConnectionAndCloseDataSourceHandler;
import com.sqlapp.jdbc.ReleaseConnectionHandler;
import com.sqlapp.jdbc.ReleaseConnectionOnlyHandler;
import com.sqlapp.jdbc.function.ExceptionConsumer;
import com.sqlapp.jdbc.function.SQLConsumer;
import com.sqlapp.util.OutputTextBuilder;

public abstract class AbstractDataSourceCommand extends AbstractCommand {

	private DataSource dataSource;

	private boolean closeDataSource = true;

	private final Converters converters = newConverters();

	private ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler = new ReleaseConnectionAndCloseDataSourceHandler();

	private ReleaseConnectionHandler releaseConnectionHandler = new ReleaseConnectionOnlyHandler();

	private SQLConsumer<Connection> commitHandler = conn -> conn.commit();

	private SQLConsumer<Connection> rollbackHandler = conn -> conn.rollback();

	protected Converters newConverters() {
		final Converters converters = new Converters();
		final TimestampConverter converter = converters.getConverter(Timestamp.class);
		converter.getZonedDateTimeConverter().setFormat("uuuu-MM-dd HH:mm:ss");
		return converters;
	}

	protected OutputTextBuilder createOutputTextBuilder() {
		final OutputTextBuilder builder = new OutputTextBuilder();
		builder.setConverters(converters);
		return builder;
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションとデータソースのクローズを行います
	 * 
	 * @param dataSource DataSource
	 * @param cons       行う処理
	 */
	protected void execute(DataSource dataSource, ExceptionConsumer<Connection> cons) {
		if (this.isCloseDataSource()) {
			executeTranInternal(dataSource, cons, releaseConnectionAndCloseDataSourceHandler);
		} else {
			executeTranInternal(dataSource, cons, releaseConnectionHandler);
		}
	}

	/**
	 * データソースからコネクションを取得して処理を行い、コネクションとデータソースのクローズを行います
	 * 
	 * @param dataSource               DataSource
	 * @param cons                     行う処理
	 * @param releaseConnectionHandler ReleaseConnectionHandler
	 */
	private void executeTranInternal(DataSource dataSource, ExceptionConsumer<Connection> cons,
			ReleaseConnectionHandler releaseConnectionHandler) {
		Connection connection;
		try {
			connection = dataSource.getConnection();
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
			execute(() -> {
				if (this.releaseConnectionAndCloseDataSourceHandler != null) {
					this.releaseConnectionAndCloseDataSourceHandler.accept(dataSource, null);
				}
			});
			throw new RuntimeException(e);
		}
		try {
			connection.setAutoCommit(false);
			cons.accept(connection);
			commit(connection);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			rollback(connection);
			getExceptionHandler().handle(e);
		} finally {
			execute(() -> {
				if (releaseConnectionHandler != null) {
					releaseConnectionHandler.accept(dataSource, connection);
				}
			});
		}
	}

	protected void commit(Connection connection) {
		execute(() -> {
			if (commitHandler != null) {
				commitHandler.accept(connection);
				this.debug("commit");
			}
		});
	}

	protected void rollback(Connection connection) {
		execute(() -> {
			if (rollbackHandler != null) {
				rollbackHandler.accept(connection);
				this.info("rollback");
			}
		});
	}

	/**
	 * @return the dataSource
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	/**
	 * @param dataSource the dataSource to set
	 */
	public void setDataSource(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * @return the dialect
	 */
	public Dialect getDialect(Connection connection) {
		return DialectResolver.getInstance().getDialect(connection);
	}

	protected String getCurrentCatalogName(final Connection connection) throws SQLException {
		return connection.getCatalog();
	}

	protected String getCurrentSchemaName(final Connection connection) throws SQLException {
		return connection.getSchema();
	}

	/**
	 * @param releaseConnectionAndCloseDataSourceHandler the
	 *                                                   releaseConnectionAndCloseDataSourceHandler
	 *                                                   to set
	 */
	public void setReleaseConnectionAndCloseDataSourceHandler(
			ReleaseConnectionHandler releaseConnectionAndCloseDataSourceHandler) {
		this.releaseConnectionAndCloseDataSourceHandler = releaseConnectionAndCloseDataSourceHandler;
	}

	/**
	 * @param releaseConnectionHandler the releaseConnectionHandler to set
	 */
	public void setReleaseConnectionHandler(ReleaseConnectionHandler releaseConnectionHandler) {
		this.releaseConnectionHandler = releaseConnectionHandler;
	}

	/**
	 * @param commitHandler the commitHandler to set
	 */
	public void setCommitHandler(SQLConsumer<Connection> commitHandler) {
		this.commitHandler = commitHandler;
	}

	/**
	 * @param rollbackHandler the rollbackHandler to set
	 */
	public void setRollbackHandler(SQLConsumer<Connection> rollbackHandler) {
		this.rollbackHandler = rollbackHandler;
	}

	/**
	 * @return the closeDataSource
	 */
	public boolean isCloseDataSource() {
		return closeDataSource;
	}

	/**
	 * @param closeDataSource the closeDataSource to set
	 */
	public void setCloseDataSource(boolean closeDataSource) {
		this.closeDataSource = closeDataSource;
	}

}
