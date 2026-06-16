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

import java.util.List;

import com.sqlapp.data.db.command.properties.EqualsHandlerProperty;
import com.sqlapp.data.db.command.properties.SchemaOptionProperty;
import com.sqlapp.data.db.command.properties.SqlFactoryRegistryProperty;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.State;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * 差分Operation生成コマンド
 * 
 * @author tatsuo satoh
 *
 */
@Getter
@Setter
public class GenerateDiffSqlCommand extends AbstractCommand
		implements SchemaOptionProperty, EqualsHandlerProperty, SqlFactoryRegistryProperty {
	/**
	 * Output originalFilePath
	 */
	private DbCommonObject<?> original;
	/**
	 * Output targetFilePath
	 */
	private DbCommonObject<?> target;

	private EqualsHandler equalsHandler = new DefaultSchemaEqualsHandler();

	private SqlFactoryRegistry sqlFactoryRegistry;

	private List<SqlOperation> sqlOperations = CommonUtils.list();;

	private Options schemaOptions = new Options();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected void doRun() {
		sqlOperations = CommonUtils.list();
		if (this.getTarget() instanceof DbObject) {
			final DbObject original = (DbObject) this.getOriginal();
			final DbObject target = (DbObject) this.getTarget();
			final DbObjectDifference difference = original.diff(target, getEqualsHandler());
			final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry(target);
			if (this.getSchemaOptions() != null) {
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			final SqlFactory<?> sqlFactory = getOperationFactory(sqlFactoryRegistry, difference);
			sqlOperations.addAll(sqlFactory.createDiffSql(difference));
		} else {
			final DbObjectCollection original = (DbObjectCollection) this.getOriginal();
			final DbObjectCollection target = (DbObjectCollection) this.getTarget();
			final DbObjectDifferenceCollection differences = original.diff(target, getEqualsHandler());
			final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry(target);
			if (this.getSchemaOptions() != null) {
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			for (final DbObjectDifference difference : differences.getList(State.Deleted)) {
				final SqlFactory<?> sqlFactory = getOperationFactory(sqlFactoryRegistry, difference);
				sqlOperations.addAll(sqlFactory.createDiffSql(difference));
			}
			for (final DbObjectDifference difference : differences.getList(State.Added, State.Modified)) {
				final SqlFactory<?> sqlFactory = getOperationFactory(sqlFactoryRegistry, difference);
				sqlOperations.addAll(sqlFactory.createDiffSql(difference));
			}
		}
	}

	private SqlFactoryRegistry getSqlFactoryRegistry(final DbCommonObject<?> target) {
		final SqlFactoryRegistry sqlFactoryRegistry = getSqlFactoryRegistry();
		if (sqlFactoryRegistry == null) {
			final Dialect dialect = SchemaUtils.getDialect(target);
			return dialect.createSqlFactoryRegistry();
		}
		return sqlFactoryRegistry;
	}

	private SqlFactory<?> getOperationFactory(final SqlFactoryRegistry sqlFactoryRegistry,
			final DbObjectDifference difference) {
		return sqlFactoryRegistry.getSqlFactory(difference);
	}

	/**
	 * swap original and target
	 */
	public void swap() {
		final DbCommonObject<?> original = this.original;
		final DbCommonObject<?> target = this.target;
		this.original = target;
		this.target = original;
	}

}
