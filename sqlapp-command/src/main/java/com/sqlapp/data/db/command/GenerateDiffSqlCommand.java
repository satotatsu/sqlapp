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
/**
 * 差分Operation生成コマンド
 * @author tatsuo satoh
 *
 */
public class GenerateDiffSqlCommand extends AbstractCommand{
	/**
	 * Output originalFilePath
	 */
	private DbCommonObject<?> original;
	/**
	 * Output targetFilePath
	 */
	private DbCommonObject<?> target;
	
	private EqualsHandler equalsHandler=DefaultSchemaEqualsHandler.getInstance();

	private SqlFactoryRegistry sqlFactoryRegistry;

	private List<SqlOperation> sqlOperations=CommonUtils.list();;
	
	private Options schemaOptions=null;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	protected void doRun() {
		sqlOperations=CommonUtils.list();
		if (this.getTarget() instanceof DbObject) {
			final DbObject original = (DbObject)this.getOriginal();
			final DbObject target = (DbObject)this.getTarget();
			final DbObjectDifference difference=original.diff(target, getEqualsHandler());
			final SqlFactoryRegistry sqlFactoryRegistry=getSqlFactoryRegistry(target);
			if (this.getSchemaOptions()!=null){
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			final SqlFactory<?> sqlFactory=getOperationFactory(sqlFactoryRegistry, difference);
			sqlOperations.addAll(sqlFactory.createDiffSql(difference));
		} else {
			final DbObjectCollection original = (DbObjectCollection)this.getOriginal();
			final DbObjectCollection target = (DbObjectCollection)this.getTarget();
			final DbObjectDifferenceCollection differences=original.diff(target, getEqualsHandler());
			final SqlFactoryRegistry sqlFactoryRegistry=getSqlFactoryRegistry(target);
			if (this.getSchemaOptions()!=null){
				sqlFactoryRegistry.setOption(this.getSchemaOptions());
			}
			for(final DbObjectDifference difference:differences.getList(State.Deleted)){
				final SqlFactory<?> sqlFactory=getOperationFactory(sqlFactoryRegistry, difference);
				sqlOperations.addAll(sqlFactory.createDiffSql(difference));
			}
			for(final DbObjectDifference difference:differences.getList(State.Added, State.Modified)){
				final SqlFactory<?> sqlFactory=getOperationFactory(sqlFactoryRegistry, difference);
				sqlOperations.addAll(sqlFactory.createDiffSql(difference));
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
	
	private SqlFactory<?> getOperationFactory(final SqlFactoryRegistry sqlFactoryRegistry, final DbObjectDifference difference){
		return sqlFactoryRegistry.getSqlFactory(difference);
	}
	
	/**
	 * @return the operations
	 */
	public List<SqlOperation> getSqlOperations() {
		return sqlOperations;
	}

	/**
	 * @return the equalsHandler
	 */
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	/**
	 * @param equalsHandler the equalsHandler to set
	 */
	public void setEqualsHandler(final EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
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
	 * @return the original
	 */
	public DbCommonObject<?> getOriginal() {
		return original;
	}

	/**
	 * @param original the original to set
	 */
	public void setOriginal(final DbCommonObject<?> original) {
		this.original = original;
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
	 * @return the schemaOptions
	 */
	public Options getSchemaOptions() {
		return schemaOptions;
	}

	/**
	 * @param schemaOptions the schemaOptions to set
	 */
	public void setSchemaOptions(final Options schemaOptions) {
		this.schemaOptions = schemaOptions;
	}

	/**
	 * swap original and target
	 */
	public void swap(){
		final DbCommonObject<?> original=this.original;
		final DbCommonObject<?> target=this.target;
		this.original=target;
		this.target=original;
	}

}
