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

package com.sqlapp.data.db.sql;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.State;

public class EmptySqlFactoryRegistry implements SqlFactoryRegistry {

	private SqlFactoryRegistry sqlFactoryRegistry = null;

	/**
	 * @param sqlFactoryRegistry
	 *            the sqlFactoryRegistry to set
	 */
	protected void setSqlFactoryRegistry(
			SqlFactoryRegistry sqlFactoryRegistry) {
		this.sqlFactoryRegistry = sqlFactoryRegistry;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactory(
			T dbObject, State state) {
		return (U)new EmptySqlFactory<T>();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <U extends SqlFactory<?>> U getSqlFactory(
			DbObjectDifference difference) {
		return (U)new EmptySqlFactory();
	}

	@Override
	public Dialect getDialect() {
		if (sqlFactoryRegistry != null) {
			return sqlFactoryRegistry.getDialect();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactory(T dbObject,
			SqlType sqlType) {
		return (U)new EmptySqlFactory<T>();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public <U extends SqlFactory<?>> U getSqlFactory(
			DbObjectDifference difference, SqlType state) {
		return (U)new EmptySqlFactory();
	}

	@Override
	public void registerSqlFactory(Class<?> objectClass, SqlType sqlType,
			Class<? extends SqlFactory<?>> sqlFactoryClass) {
	}

	@Override
	public Options getOption() {
		if (sqlFactoryRegistry != null) {
			return sqlFactoryRegistry.getOption();
		}
		return null;
	}

	@Override
	public void setOption(Options option) {

	}

	@Override
	public void deregisterSqlFactory(Class<?> objectClass, SqlType sqlType) {
		
	}

	@Override
	public void deregisterSqlFactory(Class<?> objectClass, SqlType... sqlType) {
		
	}

	@Override
	public void deregisterSqlFactory(Class<?> objectClass) {
		
	}

	@SuppressWarnings("rawtypes")
	@Override
	public SqlFactory<?> getSqlFactory(SqlType sqlType) {
		return new EmptySqlFactory();
	}

	@Override
	public void registerSqlFactory(SqlType sqlType, Class<? extends SqlFactory<?>> sqlFactoryClass) {
		
	}

	@Override
	public void deregisterSqlFactory(SqlType sqlType) {
		
	}

}
