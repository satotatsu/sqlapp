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

package com.sqlapp.data.db.command.version;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.SqlConverter;
/**
 * 
 * @author satot
 *
 */
public class VersionInsertCommand extends VersionUpCommand{

	@Override
	protected List<Row> getVersionRows(final Table table, final List<SqlFile> sqlFiles, final DbVersionHandler dbVersionHandler){
		final List<Row> rows=dbVersionHandler.getRowsForVersionUp(table, getLastChangeToApply());
		return rows;
	}

	@Override
	protected void executeSql(final Connection connection, final SqlConverter sqlConverter, final Long id, final Map<Long, SqlFile> sqlFileMap){
		
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
