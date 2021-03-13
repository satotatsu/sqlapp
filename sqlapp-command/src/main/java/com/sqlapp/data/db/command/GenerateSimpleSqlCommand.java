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

import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.util.CommonUtils;
/**
 * Operation生成コマンド
 * @author tatsuo satoh
 *
 */
public class GenerateSimpleSqlCommand extends AbstractCommand{
	/**
	 * Output targetFilePath
	 */
	private DbCommonObject<?> target;

	private SqlFactoryRegistry sqlFactoryRegistry;

	private List<SqlOperation> sqlOperations=CommonUtils.list();
	
	private SqlType sqlType=SqlType.CREATE;
	
	private Options schemaOptions=null;
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void doRun() {
		sqlOperations=CommonUtils.list();
		if (this.getTarget() instanceof DbObject) {
			final DbObject<? extends DbObject<?>> target = (DbObject<? extends DbObject<?>>)this.getTarget();
			final SqlFactoryRegistry sqlFactoryRegistry=getSqlFactoryRegistry(target);
			if (this.getSchemaOptions()!=null){
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			final SqlFactory sqlFactory=getSqlFactory(sqlFactoryRegistry, target);
			sqlOperations.addAll(sqlFactory.createSql(target));
		} else {
			final DbObjectCollection<DbObject<? extends DbObject<?>>> targetCollection = (DbObjectCollection<DbObject<? extends DbObject<?>>>)this.getTarget();
			final SqlFactoryRegistry sqlFactoryRegistry=getSqlFactoryRegistry(targetCollection);
			if (this.getSchemaOptions()!=null){
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			for(final DbObject<? extends DbObject<?>> dbObject:targetCollection){
				final SqlFactory sqlFactory=getSqlFactory(sqlFactoryRegistry, dbObject);
				sqlOperations.addAll(sqlFactory.createSql(dbObject));
			}
		}
	}
	
	private SqlFactoryRegistry getSqlFactoryRegistry(final DbCommonObject<?> target){
		final SqlFactoryRegistry sqlFactoryRegistry=getSqlFactoryRegistry();
		if (sqlFactoryRegistry==null){
			final Dialect dialect=SchemaUtils.getDialect(target);
			return dialect.createSqlFactoryRegistry();
		}
		return sqlFactoryRegistry;
	}
	
	protected SqlFactory<? extends DbCommonObject<?>> getSqlFactory(final SqlFactoryRegistry sqlFactoryRegistry, final DbObject<?> target){
		return sqlFactoryRegistry.getSqlFactory(target, getSqlType());
	}

	/**
	 * @return the sqlFactoryRegistry
	 */
	public SqlFactoryRegistry getSqlFactoryRegistry() {
		return sqlFactoryRegistry;
	}

	/**
	 * @param sqlFactoryRegistry the sqlFactoryRegistry to set
	 */
	public void setSqlFactoryRegistry(final SqlFactoryRegistry sqlFactoryRegistry) {
		this.sqlFactoryRegistry = sqlFactoryRegistry;
	}

	/**
	 * @return the target
	 */
	public DbCommonObject<?> getTarget() {
		return target;
	}

	/**
	 * @param target the target to set
	 */
	public void setTarget(final DbCommonObject<?> target) {
		this.target = target;
	}

	/**
	 * @return the sqlType
	 */
	public SqlType getSqlType() {
		return sqlType;
	}

	/**
	 * @param sqlType the sqlType to set
	 */
	public void setSqlType(final SqlType sqlType) {
		this.sqlType = sqlType;
	}

	/**
	 * @return the operations
	 */
	public List<SqlOperation> getOperations() {
		return sqlOperations;
	}

	/**
	 * @return the schemaOptions
	 */
	public Options getSchemaOptions() {
		return schemaOptions;
	}

	/**
	 * @param schemaOptions the schemaOption to set
	 */
	public void setSchemaOption(final Options schemaOptions) {
		this.schemaOptions = schemaOptions;
	}

}
