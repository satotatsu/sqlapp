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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.sql.JdbcHandler;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.jdbc.sql.node.SqlNode;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.FileUtils;

public class SqlExecuteCommand extends AbstractSqlCommand{

	private Collection<File> sqlFiles=null;

	private Collection<String> sqlText=null;

	@Override
	protected void doRun() {
		Connection connection=null;
		try{
			connection=this.getConnection();
			connection.setAutoCommit(false);
			final Dialect dialect=this.getDialect(connection);
			final SqlSplitter sqlSplitter=dialect.createSqlSplitter();
			final SqlConverter sqlConverter=getSqlConverter();
			if (!CommonUtils.isEmpty(this.getSqlFiles())) {
				for(final File file:getSqlFiles()){
					if (!file.exists()) {
						continue;
					}
					if (file.isFile()) {
						executeSql(sqlSplitter, sqlConverter, dialect, connection, file);
					}else if (file.isDirectory()) {
						final File[] children=file.listFiles();
						if (children!=null) {
							for(final File child:children) {
								executeSql(sqlSplitter, sqlConverter, dialect, connection, child);
							}
						}
					}
				}
			}
			if (!CommonUtils.isEmpty(this.getSqlText())) {
				for(final String text:getSqlText()){
					final ParametersContext context=new ParametersContext();
					context.putAll(this.getContext());
					final List<SplitResult> sqls=sqlSplitter.parse(text);
					for(final SplitResult splitResult:sqls){
						executeSql(sqlConverter, dialect, connection, splitResult);
					}
				}
			}
			connection.commit();
		} catch (final RuntimeException e) {
			rollback(connection);
			this.getExceptionHandler().handle(e);
		} catch (final SQLException e) {
			if (connection!=null){
				try {
					connection.rollback();
				} catch (final SQLException e1) {
				}
			}
			this.getExceptionHandler().handle(e);
		} finally {
			releaseConnection(connection);
		}
	}
	
	private void executeSql(final SqlSplitter sqlSplitter, final SqlConverter sqlConverter, final Dialect dialect, final Connection connection, final File file) {
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		final String text=FileUtils.readText(file, this.getEncoding());
		final List<SplitResult> sqls=sqlSplitter.parse(text);
		for(final SplitResult splitResult:sqls){
			executeSql(sqlConverter, dialect, connection, splitResult);
		}
	}

	private void executeSql(final SqlConverter sqlConverter, final Dialect dialect, final Connection connection, final SplitResult splitResult) {
		final ParametersContext context=new ParametersContext();
		context.putAll(this.getContext());
		final SqlNode sqlNode=sqlConverter.parseSql(context, splitResult.getText());
		final JdbcHandler jdbcHandler=dialect.createJdbcHandler(sqlNode);
		jdbcHandler.execute(connection, context);
	}

	
	/**
	 * @return the sqls
	 */
	protected Collection<File> getSqlFiles() {
		return sqlFiles;
	}

	/**
	 * @param sqlFiles the sqlFiles to set
	 */
	public void setSqlFiles(final Collection<File> sqlFiles) {
		this.sqlFiles = sqlFiles;
	}

	/**
	 * @param sqlFiles the sqlFiles to set
	 */
	public void setSqlFiles(final File... sqlFiles) {
		if (CommonUtils.isEmpty(sqlFiles)){
			this.sqlFiles = Collections.emptyList();
		} else{
			this.sqlFiles = CommonUtils.list(sqlFiles);
		}
	}

	/**
	 * @param sqlFiles the sqlFiles to set
	 */
	public void setSqlFiles(final String... sqlFiles) {
		if (CommonUtils.isEmpty(sqlFiles)){
			this.sqlFiles = Collections.emptyList();
		} else{
			this.sqlFiles = CommonUtils.list();
			for(final String sqlPath:sqlFiles){
				this.sqlFiles.add(new File(sqlPath));
			}
		}
	}

	/**
	 * @return the sqlText
	 */
	protected Collection<String> getSqlText() {
		return sqlText;
	}

	/**
	 * @param sqlText the sqlText to set
	 */
	public void setSqlText(final Collection<String> sqlText) {
		this.sqlText = sqlText;
	}
	
	/**
	 * @param args the sqlText to set
	 */
	public void setSqlText(final String... args) {
		if (CommonUtils.isEmpty(args)){
			this.sqlText = Collections.emptyList();
		} else{
			this.sqlText = CommonUtils.list();
			for(final String arg:args){
				if (!CommonUtils.isEmpty(arg)){
					this.sqlText.add(arg);
				}
			}
		}
	}

}
