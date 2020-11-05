/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.jdbc;

import static com.sqlapp.util.CommonUtils.*;
import static com.sqlapp.util.DbUtils.close;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class AbstractResultSetHandler<T> extends AbstractHandler<T>{

	public List<T> handle(PreparedStatement statement){
		ResultSet resultSet=null;
		List<T> list=list();
		try {
			resultSet = statement.executeQuery();
			while(resultSet.next()){
				T t=handle(resultSet);
				list.add(t);
			}
		} catch (SQLException e) {
			throw handleException(e);
		} finally{
			close(resultSet);
			close(statement);
		}
		return list;
	}
	
	protected abstract T handle(ResultSet resultSet);
}
