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
import java.util.Map;
import java.util.Set;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbLink;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Function;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Mview;
import com.sqlapp.data.schemas.PackageBody;
import com.sqlapp.data.schemas.Partition;
import com.sqlapp.data.schemas.Procedure;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.RowCollection;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.SubPartition;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableLink;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.Type;
import com.sqlapp.data.schemas.TypeBody;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.SimpleBeanUtils;

public class SimpleSqlFactoryRegistry implements SqlFactoryRegistry {
	/**
	 * All Sql Factories
	 */
	private final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactories = CommonUtils
			.map();
	/**
	 * All Object Sql Factories
	 */
	private Map<Class<?>, Map<SqlType, Class<? extends SqlFactory<?>>>> objectSqlFactories = CommonUtils
			.map();

	/**
	 * All Object State Sql Factories
	 */
	private Map<Class<?>, Map<State, List<SqlType>>> objectStateSqlFactories = CommonUtils
			.map();

	private final Dialect dialect;
	/**
	 * Sql Factoryが見つからなかった場合のファクトリ
	 */
	private SqlFactoryRegistry notFoundSqlFactoryRegistry = new EmptySqlFactoryRegistry();

	/**
	 * デフォルトのオペレーションオプション
	 */
	private Options option = new Options();

	public SimpleSqlFactoryRegistry(final Dialect dialect) {
		this.dialect = dialect;
		initialize();
	}

	protected void initialize() {
		initializeAllSqls();
		registerDropSqlFactories();
		getObjectSqlFactories().forEach((k,v)->{
			v.forEach((k1,v1)->{
				final SqlType sqlType = k1;
				if (sqlType.getState() != null) {
					registerSqlFactory(k, sqlType.getState(),
							sqlType);
				}
			});
		});
		initializeAllStateSqls();
	}

	/**
	 * SQLOperationを登録します
	 */
	protected void initializeAllSqls() {
		// Table
		initializeTableSqls();
		// Row
		initializeRowSqls();
		// Catalog
		registerSqlFactory(Catalog.class, SqlType.CREATE,
				CreateCatalogFactory.class);
		registerSqlFactory(Catalog.class, SqlType.ALTER,
				AlterCatalogFactory.class);
		// Schema
		registerSqlFactory(Schema.class, SqlType.CREATE,
				CreateSchemaFactory.class);
		registerSqlFactory(Schema.class, SqlType.ALTER,
				AlterSchemaFactory.class);
		// Index
		registerSqlFactory(Index.class, SqlType.CREATE,
				CreateIndexFactory.class);
		//CheckConstraint
		registerSqlFactory(CheckConstraint.class, SqlType.CREATE,
				CreateCheckConstraintFactory.class);
		//UniqueConstraint
		registerSqlFactory(UniqueConstraint.class, SqlType.CREATE,
				CreateUniqueConstraintFactory.class);
		//ForeignKeyConstraint
		registerSqlFactory(ForeignKeyConstraint.class, SqlType.CREATE,
				CreateForeignKeyConstraintFactory.class);
		// View
		registerSqlFactory(View.class, SqlType.CREATE,
				CreateViewFactory.class);
		registerSqlFactory(View.class, SqlType.DROP,
				DropViewFactory.class);
	}

	private void initializeTableSqls() {
		// Table
		initializeTableDclSqls();
		initializeTableDdlSqls();
		initializeTableDmlSqls();
	}

	private void initializeTableDclSqls() {
		
	}

	private void initializeTableDdlSqls() {
		// Table
		registerSqlFactory(Table.class, SqlType.CREATE,
				CreateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.ALTER,
				AlterTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DROP,
				DropTableFactory.class);
		registerSqlFactory(Table.class, SqlType.TRUNCATE,
				TruncateTableFactory.class);
	}

	private void initializeTableDmlSqls() {
		// Table
		registerSqlFactory(Table.class, SqlType.DELETE_ALL,
				DeleteAllTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DELETE,
				DeleteTableFactory.class);
		registerSqlFactory(Table.class, SqlType.DELETE_BY_PK,
				DeleteByPkTableFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT,
				SelectTableFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT_ALL,
				SelectAllTableFactory.class);
		registerSqlFactory(Table.class, SqlType.SELECT_BY_PK,
				SelectByPkTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT,
				InsertTableFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE,
				UpdateTableFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE_ALL,
				UpdateAllTableFactory.class);
		registerSqlFactory(Table.class, SqlType.UPDATE_BY_PK,
				UpdateByPkTableFactory.class);
		registerSqlFactory(Table.class, SqlType.INSERT_SELECT_BY_PK,
				InsertSelectTableFactory.class);
		registerSqlFactory(Table.class, SqlType.MERGE_BY_PK,
				MergeByPkTableFactory.class);
	}
	
	private void initializeRowSqls() {
		// Row
		registerRowSqlFactory(SqlType.INSERT_ROW,
				InsertRowFactory.class);
		registerRowSqlFactory(SqlType.UPDATE_ROW,
				UpdateRowFactory.class);
		registerRowSqlFactory(SqlType.DELETE_ROW,
				DeleteRowFactory.class);
		registerRowSqlFactory(SqlType.INSERT_SELECT_ROW,
				InsertSelectRowFactory.class);
		registerRowSqlFactory(SqlType.MERGE_ROW, MergeRowFactory.class);
	}
	
	protected void registerRowSqlFactory(final SqlType sqlType,
			 final Class<? extends SqlFactory<?>> commandClass) {
		registerSqlFactory(RowCollection.class, sqlType, commandClass);
		registerSqlFactory(Row.class, sqlType, commandClass);
	}

	protected void initializeAllStateSqls() {
		regiserDefaultStateSqlFactory(Table.class);
		registerSqlFactory(Table.class, State.Modified, SqlType.ALTER);
		//
		regiserDefaultStateSqlFactory(View.class);
		//
		regiserDefaultStateSqlFactory(Mview.class);
		//
		regiserDefaultStateSqlFactory(Trigger.class);
		//
		regiserDefaultStateSqlFactory(Procedure.class);
		//
		regiserDefaultStateSqlFactory(Function.class);
		//
		regiserDefaultStateSqlFactory(Package.class);
		//
		regiserDefaultStateSqlFactory(PackageBody.class);
		//
		regiserDefaultStateSqlFactory(Domain.class);
		//
		regiserDefaultStateSqlFactory(Type.class);
		//
		regiserDefaultStateSqlFactory(TypeBody.class);
		//
		regiserDefaultStateSqlFactory(Schema.class);
		//
		regiserDefaultStateSqlFactory(DbLink.class);
		//
		regiserDefaultStateSqlFactory(TableLink.class);
		//
		registerSqlFactory(Row.class, State.Added, SqlType.INSERT);
		registerSqlFactory(Row.class, State.Deleted, SqlType.DELETE_BY_PK);
		registerSqlFactory(Row.class, State.Modified, SqlType.UPDATE);
	}

	private void regiserDefaultStateSqlFactory(final Class<?> clazz) {
		registerSqlFactory(clazz, State.Added, SqlType.CREATE);
		registerSqlFactory(clazz, State.Deleted, SqlType.DROP);
	}

	/**
	 * SqlFactoryを登録します
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param state state
	 * @param sqlTypes sqlTypes
	 */
	protected void registerSqlFactory(final Class<?> objectClass, final State state,
			final SqlType... sqlTypes) {
		registerSqlFactory(objectClass, state,
				CommonUtils.list(sqlTypes));
	}

	/**
	 * SqlFactoryを登録します
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param state state
	 * @param sqlTypes sqlTypes
	 */
	protected void registerSqlFactory(final Class<?> objectClass,
			final State state, final List<SqlType> sqlTypes) {
		Map<State, List<SqlType>> stateOperations = getObjectStateSqlFactories()
				.get(objectClass);
		if (stateOperations == null) {
			stateOperations = CommonUtils.map();
			getObjectStateSqlFactories().put(objectClass, stateOperations);
			if (objectClass==Partition.class) {
				getObjectStateSqlFactories().put(SubPartition.class, stateOperations);
			}
		}
		stateOperations.put(state, sqlTypes);
	}

	/**
	 * SqlFactoryを登録します
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param sqlType sqlType
	 */
	@Override
	public void deregisterSqlFactory(final Class<?> objectClass, final SqlType sqlType) {
		final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap = getFromObjectSqlFactories(
				objectClass);
		if (sqlFactoryMap != null) {
			sqlFactoryMap.remove(sqlType);
		}
	}

	/**
	 * SqlFactoryを登録します
	 * 
	 *            登録対象のDBオブジェクトクラス名
	 * @param sqlFactoryClass
	 */
	@Override
	public void deregisterSqlFactory(final Class<?> sqlFactoryClass) {
		getObjectSqlFactories().remove(
				sqlFactoryClass);
	}
	
	/**
	 * SqlFactoryを登録します
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param sqlTypes sqlTypes
	 */
	@Override
	public void deregisterSqlFactory(final Class<?> objectClass, final SqlType... sqlTypes) {
		final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap = getFromObjectSqlFactories(
				objectClass);
		if (sqlFactoryMap != null) {
			if (sqlTypes!=null){
				for(final SqlType sqlType:sqlTypes){
					sqlFactoryMap.remove(sqlType);
				}
			}
		}
	}

	/**
	 * SqlFactoryを登録します
	 * 
	 * @param objectClass
	 *            登録対象のDBオブジェクトクラス名
	 * @param sqlType sqlType
	 * @param sqlFactoryClass sqlFactoryClass
	 */
	@Override
	public void registerSqlFactory(final Class<?> objectClass,
			final SqlType sqlType, final Class<? extends SqlFactory<?>> sqlFactoryClass) {
		Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactories = getFromObjectSqlFactories(
				objectClass);
		if (sqlFactories == null) {
			sqlFactories = CommonUtils.map();
			getObjectSqlFactories().put(objectClass, sqlFactories);
		}
		registerSqlFactory(sqlFactories, sqlType,
				sqlFactoryClass);
	}

	/**
	 * DROP用のSqlFactoryを一括登録します
	 * 
	 */
	protected void registerDropSqlFactories() {
		final Set<Class<?>> objectClass = SchemaUtils.getDroppableClasses();
		final Set<Class<?>> supportedClass = this.getDialect().supportedSchemaTypes();
		final Set<Class<?>> dropableClass = CommonUtils.and(objectClass,
				supportedClass);
		for (final Class<?> clazz : dropableClass) {
			final Class<SqlFactory<?>> sqlFactoryClazz = getSqlFactoryClass(clazz,
					SqlType.DROP);
			if (sqlFactoryClazz == null) {
				registerSqlFactory(clazz, SqlType.DROP,
						DropNamedObjectFactory.class);
				if (clazz==Partition.class) {
					registerSqlFactory(SubPartition.class, SqlType.DROP,
							DropNamedObjectFactory.class);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private  Class<SqlFactory<?>> getSqlFactoryClass(final Class<?> clazz,
			final SqlType sqlType) {
		final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap = getFromObjectSqlFactories(clazz);
		if (sqlFactoryMap == null) {
			return null;
		}
		return (Class<SqlFactory<?>>)sqlFactoryMap.get(sqlType);
	}

	/**
	 * SqlFactoryが見つからなかった場合に呼ばれるメソッド
	 * 
	 * @param dbObject
	 * @param state
	 */
	protected <T extends DbCommonObject<?>, U extends SqlFactory<?>> U handleUnknownOperation(
			final T dbObject, final State state) {
		return notFoundSqlFactoryRegistry.getSqlFactory(dbObject,
				state);
	}

	private void registerSqlFactory(
			final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactories, final SqlType sqlType,
			final Class<? extends SqlFactory<?>> sqlFactoryClass) {
		sqlFactories.put(sqlType, sqlFactoryClass);
	}

	@SuppressWarnings("rawtypes")
	public <T> T newInstance(final Class clazz) {
		if (clazz == null) {
			return null;
		}
		final T command = SimpleBeanUtils.getInstance(clazz).newInstance(this);
		initialize((SqlFactory<?>) command);
		return command;
	}

	protected <T> void initialize(final SqlFactory<?> sqlFactory) {
		SimpleBeanUtils.setValue(sqlFactory,
				"sqlFactoryRegistry", this);
	}

	@SuppressWarnings("unchecked")
	protected <T extends DbCommonObject<?>, U extends SqlFactory<?>> U initializeSqls(
			final T dbObject, final SqlFactory<?> sqlFactory) {
		if (this.getOption()!=null&&sqlFactory!=null) {
			sqlFactory.setOptions(this.getOption().clone());
		}
		return (U)sqlFactory;
	}

	/**
	 * @return the objectStateSqlFactories
	 */
	protected Map<Class<?>, Map<State, List<SqlType>>> getObjectStateSqlFactories() {
		return objectStateSqlFactories;
	}

	/**
	 * @param objectStateSqlFactories
	 *            the objectStateSqlFactories to set
	 */
	protected void setObjectStateSqlFactories(
			final Map<Class<?>, Map<State, List<SqlType>>> objectStateSqlFactories) {
		this.objectStateSqlFactories = objectStateSqlFactories;
	}

	/**
	 * @return the objectSqlFactories
	 */
	protected Map<Class<?>, Map<SqlType, Class<? extends SqlFactory<?>>>> getObjectSqlFactories() {
		return objectSqlFactories;
	}

	/**
	 * @param objectSqlFactories
	 *            the objectSqlFactories to set
	 */
	protected void setSqlFactories(
			final Map<Class<?>, Map<SqlType, Class<? extends SqlFactory<?>>>> objectSqlFactories) {
		this.objectSqlFactories = objectSqlFactories;
	}

	/**
	 * @return the dialect
	 */
	@Override
	public Dialect getDialect() {
		return dialect;
	}

	/**
	 * @param notFoundSqlFactoryRegistry
	 *            the notFoundSqlFactoryRegistry to set
	 */
	public void setNotFoundSqlRegistry(
			final SqlFactoryRegistry notFoundSqlFactoryRegistry) {
		this.notFoundSqlFactoryRegistry = notFoundSqlFactoryRegistry;
		if (this.notFoundSqlFactoryRegistry != null
				&& this.notFoundSqlFactoryRegistry instanceof EmptySqlFactoryRegistry) {
			((EmptySqlFactoryRegistry) notFoundSqlFactoryRegistry)
					.setSqlFactoryRegistry(this);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends DbCommonObject<?>, U extends SqlFactory<?>> U  getSqlFactory(
			final T dbObject, final SqlType sqlType) {
		SqlFactory<?> sqlFactory = null;
		if (dbObject instanceof Table) {
			sqlFactory = getSqlFactoryInternal((Table) dbObject, sqlType);
		} else {
			sqlFactory = getSqlFactoryBySqlTypeFromAll(
					dbObject.getClass(), sqlType);
		}
		if (sqlFactory == null) {
			sqlFactory = handleUnknownSqlFactory(dbObject, sqlType);
		}
		return (U)sqlFactory;
	}

	/**
	 * SqlFactoryが見つからなかった場合に呼ばれるメソッド
	 * 
	 * @param dbObject dbObject
	 * @param sqlType sqlType
	 */
	@SuppressWarnings("unchecked")
	protected <T extends DbCommonObject<?>, U extends SqlFactory<?>> U handleUnknownSqlFactory(
			final T dbObject, final SqlType sqlType) {
		return (U)this.initializeSqls(dbObject,
				notFoundSqlFactoryRegistry.getSqlFactory(dbObject,
						sqlType));
	}

	@SuppressWarnings("unchecked")
	protected <T extends DbCommonObject<?>, U extends SqlFactory<T>> U getSqlFactoryInternal(
			final T dbObject, final SqlType... sqlTypes) {
		if (CommonUtils.isEmpty(sqlTypes)) {
			return null;
		}
		SqlFactory<T> sqlFactory = null;
		if (sqlTypes.length == 1) {
			sqlFactory = this.getSqlFactoryInternal(dbObject,
					CommonUtils.first(sqlTypes));
		} else {
			final List<SqlFactory<?>> commands = CommonUtils.list();
			for (final SqlType sqlType : sqlTypes) {
				sqlFactory = this.getSqlFactoryInternal(dbObject, sqlType);
				commands.add(sqlFactory);
			}
			final CompositeSqlFactory compositeOperation = new CompositeSqlFactory(
					commands);
			sqlFactory = (SqlFactory<T>) compositeOperation;
		}
		return (U)sqlFactory;
	}

	protected <T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactoryInternal(
			final T dbObject, final SqlType sqlType) {
		final U sqlFactory = getSqlFactoryBySqlTypeFromAll(
				dbObject.getClass(), sqlType);
		return initializeSqls(dbObject, sqlFactory);
	}

	private <U extends SqlFactory<?>> U getSqlFactoryBySqlTypeFromAll(
			final Class<?> clazz, final SqlType sqlType) {
		final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactorys = getFromObjectSqlFactories(clazz);
		if (sqlFactorys == null) {
			if (sqlFactorys == null) {
				return null;
			}
		}
		final Class<? extends SqlFactory<?>> sqlFactoryClass = sqlFactorys.get(sqlType);
		if (sqlFactoryClass == null) {
			return null;
		}
		final U sqlFactory = newInstance(sqlFactoryClass);
		return sqlFactory;
	}
	
	private Map<SqlType, Class<? extends SqlFactory<?>>> getFromObjectSqlFactories(final Class<?> clazz){
		Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactorys = getObjectSqlFactories().get(
				clazz);
		if (sqlFactorys == null) {
			if (clazz==SubPartition.class) {
				sqlFactorys = getObjectSqlFactories().get(
						Partition.class);
			}
		}
		return sqlFactorys;
	}

	@Override
	public <U extends SqlFactory<?>> U getSqlFactory(
			final DbObjectDifference difference, final SqlType sqlType) {
		U obj = getSqlFactoryInternal(
				(DbCommonObject<?>)difference.getOriginal(), sqlType);
		setDialect(obj, difference.getOriginal());
		if (obj != null) {
			return obj;
		}
		obj = this.handleUnknownSqlFactory(difference, sqlType);
		setDialect(obj, difference.getOriginal());
		return obj;
	}

	/**
	 * Operationが見つからなかった場合に呼ばれるメソッド
	 * 
	 * @param difference
	 */
	protected <U extends SqlFactory<?>> U handleUnknownSqlFactory(
			final DbObjectDifference difference, final SqlType sqlType) {
		return notFoundSqlFactoryRegistry.getSqlFactory(difference,
				sqlType);
	}

	@Override
	public <T extends DbCommonObject<?>, U extends SqlFactory<?>> U getSqlFactory(
			final T dbObject, final State state) {
		List<SqlType> sqlTypes = null;
		U operation = null;
		if (dbObject instanceof DbObject) {
			sqlTypes = getSqlTypes((DbObject<?>) dbObject, state);
			final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap = this
					.getFromObjectSqlFactories(dbObject.getClass());
			if (sqlTypes != null) {
				operation = this.getSqlFactory(sqlFactoryMap,
						sqlTypes.toArray(new SqlType[0]));
			}
		}
		if (operation == null) {
			return this.handleUnknownOperation(dbObject, state);
		}
		return operation;
	}

	protected List<SqlType> getSqlTypes(final DbObject<?> object, final State state) {
		final Map<State, List<SqlType>> map = this.getObjectStateSqlFactories().get(
				object.getClass());
		if (map == null) {
			return null;
		}
		return map.get(state);
	}

	@SuppressWarnings("unchecked")
	protected <U extends SqlFactory<?>> U getSqlFactory(
			final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap, final SqlType... sqlTypes) {
		if (CommonUtils.isEmpty(sqlTypes)) {
			return null;
		}
		if (CommonUtils.isEmpty(sqlFactoryMap)) {
			return null;
		}
		U sqlFactory = null;
		if (sqlTypes.length == 1) {
			sqlFactory = newInstance(sqlFactoryMap.get(CommonUtils
					.first(sqlTypes)));
		} else {
			final List<SqlFactory<?>> operations = CommonUtils.list();
			for (final SqlType sqlType : sqlTypes) {
				sqlFactory = newInstance(sqlFactoryMap.get(sqlType));
				operations.add(sqlFactory);
			}
			final CompositeSqlFactory compositeOperation = new CompositeSqlFactory(
					operations);
			sqlFactory = (U)compositeOperation;
		}
		return sqlFactory;
	}

	protected <T extends DbCommonObject<?>, U extends SqlFactory<T>> U getSqlFactory(
			final Map<SqlType, Class<? extends SqlFactory<?>>> sqlFactoryMap, final List<SqlType> sqlTypes) {
		if (CommonUtils.isEmpty(sqlTypes)) {
			return null;
		}
		return getSqlFactory(sqlFactoryMap,
				sqlTypes.toArray(new SqlType[0]));
	}

	@Override
	public <U extends SqlFactory<?>> U getSqlFactory(
			final DbObjectDifference difference) {
		U ret = null;
		if (difference.getState() == State.Deleted) {
			ret = getSqlFactory((DbCommonObject<?>) difference.getOriginal(),
					difference.getState());
		} else {
			ret = getSqlFactory((DbCommonObject<?>) difference.getTarget(),
					difference.getState());
		}
		setDialect(ret, difference.getOriginal());
		return ret;
	}

	protected void setDialect(final SqlFactory<?> sqlFactory, final Object val) {
		if (sqlFactory == null) {
			return;
		}
		Dialect dialect = null;
		if (val != null) {
			dialect = SimpleBeanUtils.getValue(val, "dialect");
		}
		if (dialect == null) {
			dialect = this.getDialect();
		}
		SimpleBeanUtils.setValue(sqlFactory, "dialect", dialect);
	}

	/**
	 * SqlFactoryが見つからなかった場合に呼ばれるメソッド
	 * 
	 * @param difference
	 */
	protected <T extends DbCommonObject<?>, U extends SqlFactory<T>> U handleUnknownSqlFactory(
			final DbObjectDifference difference) {
		return notFoundSqlFactoryRegistry.getSqlFactory(difference);
	}

	@Override
	public Options getOption() {
		return option;
	}

	@Override
	public void setOption(final Options operationOption) {
		this.option = operationOption;
	}

	@Override
	public SqlFactory<?> getSqlFactory(final SqlType sqlType) {
		final Class<? extends SqlFactory<?>> sqlFactoryClass=this.sqlFactories.get(sqlType);
		if (sqlFactoryClass!=null){
			final SqlFactory<?> sqlFactory = newInstance(sqlFactoryClass);
			return sqlFactory;
		}
		return this.notFoundSqlFactoryRegistry.getSqlFactory(sqlType);
	}

	@Override
	public void registerSqlFactory(final SqlType sqlType, final Class<? extends SqlFactory<?>> sqlFactoryClass) {
		this.sqlFactories.put(sqlType, sqlFactoryClass);
	}

	@Override
	public void deregisterSqlFactory(final SqlType sqlType) {
		this.sqlFactories.remove(sqlType);
	}

}
