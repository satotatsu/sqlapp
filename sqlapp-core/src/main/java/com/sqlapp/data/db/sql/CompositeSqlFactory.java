/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.sql;

import java.util.List;

import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;
import com.sqlapp.util.SimpleBeanWrapper;

/**
 * 複数のOperationを複合したOperation
 * 
 * @author tatsuo satoh
 * 
 */
public class CompositeSqlFactory extends AbstractSqlFactory<DbObject<?>,AbstractSqlBuilder<?>> {

	private List<SqlFactory<?>> commands;

	public CompositeSqlFactory(SqlFactory<?>... commands) {
		this.commands = CommonUtils.list(commands);
	}

	public CompositeSqlFactory(List<SqlFactory<?>> commands) {
		this.commands = commands;
	}

	@Override
	public List<SqlOperation> createSql(DbObject<?> obj) {
		return getSqlFromObject(obj);
	}

	protected List<SqlOperation> getSqlFromObject(Object obj) {
		List<SqlOperation> result = CommonUtils.list();
		for (SqlFactory<?> command : commands) {
			List<SqlOperation> value = SimpleBeanUtils.getInstance(
					command.getClass()).invoke(command,
					SqlFactory.COMMAND_METHOD, obj);
			result.addAll(value);
		}
		return result;
	}

	protected List<SqlOperation> getSqlFromObject(Difference<?> obj) {
		List<SqlOperation> result = CommonUtils.list();
		for (SqlFactory<?> command : commands) {
			SimpleBeanWrapper beanWrapper = SimpleBeanUtils.getInstance(command
					.getClass());
			List<SqlOperation> value = null;
			if (beanWrapper.hasMethod(SqlFactory.COMMAND_METHOD, obj)) {
				value = beanWrapper.invoke(command, SqlFactory.COMMAND_METHOD,
						obj);
				result.addAll(value);
			} else {
				if (obj.getState() == State.Added) {
					value = beanWrapper.invoke(command,
							SqlFactory.COMMAND_METHOD, obj.getTarget());
				} else if (obj.getState() == State.Deleted) {
					value = beanWrapper.invoke(command,
							SqlFactory.COMMAND_METHOD, obj.getOriginal());
				} else {
					value = beanWrapper.invoke(
							command,
							SqlFactory.COMMAND_METHOD,
							obj.getTarget() != null ? obj.getTarget() : obj
									.getOriginal());
				}
				result.addAll(value);
			}
		}
		return result;
	}
}
