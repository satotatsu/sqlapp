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

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Table;
/**
 * クエリを実行して結果を標準出力に出力します。
 * @author tatsuo satoh
 *
 */
abstract class AbstractSqlQueryCommand extends AbstractDataSourceCommand{

	private String sql=null;
	private OutputFormatType outputFormatType=OutputFormatType.TSV;
	
	@Override
	protected void doRun() {
		Connection connection=null;
		try{
			connection=this.getConnection();
			final Dialect dialect=this.getDialect(connection);
			try(Statement statement=connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)){
				try(ResultSet resultSet=statement.executeQuery(getSql())){
					final Table table=new Table();
					table.setDialect(dialect);
					table.readMetaData(connection, resultSet);
					if (this.getOutputFormatType().isTable()){
						table.readData(resultSet);
						outputTableData(dialect, table);
					} else{
						outputTableData(dialect, table, resultSet);
					}
				}
			}
		} catch (final RuntimeException e) {
			this.getExceptionHandler().handle(e);
		} catch (final IOException e) {
			this.getExceptionHandler().handle(e);
		} catch (final SQLException e) {
			this.getExceptionHandler().handle(e);
		} catch (final Exception e) {
			this.getExceptionHandler().handle(e);
		} finally {
			releaseConnection(connection);
		}
	}

	protected abstract void outputTableData(final Dialect dialect, final Table table) throws Exception;

	protected abstract void outputTableData(final Dialect dialect, final Table table, final ResultSet resultSet) throws SQLException, IOException, Exception;

	/**
	 * @return the sql
	 */
	public String getSql() {
		return sql;
	}

	/**
	 * @param sql the sql to set
	 */
	public void setSql(final String sql) {
		this.sql = sql;
	}

	/**
	 * @return the outputFormatType
	 */
	public OutputFormatType getOutputFormatType() {
		return outputFormatType;
	}

	/**
	 * @param outputFormatType the outputFormatType to set
	 */
	public void setOutputFormatType(final OutputFormatType outputFormatType) {
		this.outputFormatType = outputFormatType;
	}

	
}
