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
import java.util.Map;

import com.sqlapp.data.db.command.version.DbVersionFileHandler.SqlFile;
import com.sqlapp.jdbc.sql.SqlConverter;
/**
 * 
 * @author satot
 *
 */
public class VersionDeleteCommand extends VersionDownCommand{

	@Override
	protected void executeSql(Connection connection, SqlConverter sqlConverter, Long id, Map<Long, SqlFile> sqlFileMap){
		
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
