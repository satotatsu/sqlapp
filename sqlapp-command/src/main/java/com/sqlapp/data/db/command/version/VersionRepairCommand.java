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

package com.sqlapp.data.db.command.version;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.util.SqlSplitter.SplitResult;
import com.sqlapp.data.schemas.DbConcurrencyException;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlConverter;
import com.sqlapp.util.CommonUtils;

public class VersionRepairCommand extends VersionUpCommand{

	@Override
	protected List<Row> getVersionRows(final Table table, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler){
		final Row row=dbVersionHandler.getRowsForVersionRepair(table);
		if (row==null){
			return Collections.emptyList();
		} else{
			final List<Row> rows=CommonUtils.list();
			rows.add(row);
			return rows;
		}
	}

	@Override
	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final Long id, final Map<Long, SqlFile> sqlFileMap){
		
	}
	
	@Override
	protected boolean preCheck(final Connection connection, final Dialect dialect, final Table table, final Long id, final Row row, final DbVersionHandler dbVersionHandler) throws SQLException{
		if(!dbVersionHandler.exists(dialect, connection, table, id)){
			throw new DbConcurrencyException("row="+row);
		}
		return true;
	}

	@Override
	protected boolean startVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long seriesNumber, final DbVersionHandler dbVersionHandler) throws SQLException{
		return true;
	}
	
	@Override
	protected void finalizeVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long id, final DbVersionHandler dbVersionHandler) throws SQLException{
		dbVersionHandler.deleteVersion(connection, dialect, table, row);
	}

	@Override
	protected void errorVersion(final Connection connection, final Dialect dialect, final Table table, final Row row, final Long id, final DbVersionHandler dbVersionHandler) throws SQLException{
	}

	
	@Override
	protected List<SplitResult> getSqls(final SqlFile sqlFile){
		return sqlFile.getDownSqls();
	}

	/**
	 * @return the setupSqlDirectory
	 */
	@Override
	public File getSetupSqlDirectory() {
		return null;
	}

	/**
	 * @return the finalizeSqlDirectory
	 */
	@Override
	public File getFinalizeSqlDirectory() {
		return null;
	}
}
