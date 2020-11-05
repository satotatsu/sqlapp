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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.datatype.DbDataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.properties.FormulaProperty;
import com.sqlapp.data.schemas.properties.SchemaNameProperty;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * DB上のオブジェクトを作成する抽象クラス
 * 
 * @author satoh
 * 
 * @param <T>
 */
public abstract class AbstractSqlFactory<T extends DbCommonObject<?>, S extends AbstractSqlBuilder<?>>
		implements SqlFactory<T> {
	/**
	 * Operationファクトリ
	 */
	private SqlFactoryRegistry sqlFactoryRegistry = null;

	private boolean quateObjectName = true;

	private boolean quateColumnName = true;

	/**
	 * @return the quateColumnName
	 */
	public boolean isQuateColumnName() {
		return quateColumnName;
	}

	/**
	 * @param quateColumnName
	 *            the quateColumnName to set
	 */
	public void setQuateColumnName(boolean quateColumnName) {
		this.quateColumnName = quateColumnName;
	}

	/**
	 * @return the quateObjectName
	 */
	public boolean isQuateObjectName() {
		return quateObjectName;
	}

	/**
	 * @param quateObjectName
	 *            the quateObjectName to set
	 */
	public void setQuateObjectName(boolean quateObjectName) {
		this.quateObjectName = quateObjectName;
	}

	private Dialect dialect = null;

	public Dialect getDialect() {
		if (this.dialect != null) {
			return this.dialect;
		}
		if (getSqlFactoryRegistry() == null) {
			return this.dialect;
		}
		return this.getSqlFactoryRegistry().getDialect();
	}

	/**
	 * @param dialect
	 *            the dialect to set
	 */
	public void setDialect(Dialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * @return the sqlFactoryRegistry
	 */
	@Override
	public SqlFactoryRegistry getSqlFactoryRegistry() {
		return sqlFactoryRegistry;
	}

	/**
	 * @param sqlFactoryRegistry
	 *            the sqlFactoryRegistry to set
	 */
	public void setSqlFactoryRegistry(
			SqlFactoryRegistry sqlFactoryRegistry) {
		this.sqlFactoryRegistry = sqlFactoryRegistry;
	}

	/**
	 * 文字列のコレクションを改行を含む文字列に変換します
	 * 
	 * @param args
	 *            文字列のコレクション
	 * @return 文字列
	 */
	protected String toString(Collection<String> args) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (String arg : args) {
			if (!first) {
				builder.append("\n");
			} else {
				first = false;
			}
			builder.append(arg);
		}
		return builder.toString();
	}

	protected void initialize(AbstractSqlBuilder<?> builder) {
	}

	/**
	 * SQLを取得します
	 * 
	 * @param c
	 */
	@Override
	public List<SqlOperation> createSql(Collection<T> c) {
		List<SqlOperation> list = CommonUtils.list();
		List<T> sorted=sort(CommonUtils.list(c));
		for (T obj : sorted) {
			List<SqlOperation> ret = createSql(obj);
			List<SqlOperation> startList = getStartSqlOperations(obj);
			list.addAll(startList);
			list.addAll(ret);
			List<SqlOperation> endList = getEndSqlOperations(obj);
			list.addAll(endList);
		}
		return list;
	}

	protected List<T> sort(List<T> c){
		return c;
	}

	protected void add(List<SqlOperation> list, SqlOperation... operations){
		if (CommonUtils.isEmpty(operations)){
			return;
		}
		for(SqlOperation operation:operations){
			if (operation!=null&&!CommonUtils.isEmpty(operation.getSqlText())){
				list.add(operation);
			}
		}
	}
	
	@Override
	public List<SqlOperation> createDiffSql(
			Collection<DbObjectDifference> differenceCollection) {
		List<SqlOperation> result = CommonUtils.list();
		List<DbObjectDifference> diffrences = DbObjectDifferenceCollection
				.getByStates(differenceCollection, State.Deleted);
		if (!CommonUtils.isEmpty(diffrences)) {
			SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (DbObjectDifference dbObjectDifference : diffrences) {
				List<SqlOperation> operations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(operations);
			}
		}
		diffrences = DbObjectDifferenceCollection.getByStates(
				differenceCollection, State.Added);
		if (!CommonUtils.isEmpty(diffrences)) {
			SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (DbObjectDifference dbObjectDifference : diffrences) {
				List<SqlOperation> operations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(operations);
			}
		}
		diffrences = DbObjectDifferenceCollection.getByStates(
				differenceCollection, State.Modified);
		if (!CommonUtils.isEmpty(diffrences)) {
			SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (DbObjectDifference dbObjectDifference : diffrences) {
				List<SqlOperation> dbOperations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(dbOperations);
			}
		}
		return result;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected List<DbObjectDifference> sort(SqlFactory<?> sqlFactory, List<DbObjectDifference> list){
		if (sqlFactory instanceof AbstractSqlFactory){
			return ((AbstractSqlFactory)sqlFactory).sortDbObjectDifference(list);
		}
		return list;
	}

	protected List<DbObjectDifference> sortDbObjectDifference(List<DbObjectDifference> list) {
		return list;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.sql.SqlFactory#createDiffSql(com.sqlapp.data.schemas.DbObjectDifference)
	 */
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference obj) {
		return this.createSql(getObject(obj));
	}

	@SuppressWarnings("unchecked")
	private T getObject(DbObjectDifference obj) {
		if (obj.getState() == State.Deleted) {
			return (T)obj.getOriginal();
		}
		return (T)obj.getTarget();
	}

	@SuppressWarnings("unchecked")
	protected List<SqlOperation> getStartSqlOperations(T obj) {
		return (List<SqlOperation>) Collections.EMPTY_LIST;
	}

	@SuppressWarnings("unchecked")
	protected List<SqlOperation> getEndSqlOperations(T obj) {
		return (List<SqlOperation>) Collections.EMPTY_LIST;
	}

	protected boolean addSchemaName(DbObject<?> object,
			AbstractSqlBuilder<?> builder) {
		if (object instanceof SchemaNameProperty) {
			SchemaNameProperty<?> schemaName = (SchemaNameProperty<?>) object;
			if (!CommonUtils.isEmpty(schemaName.getSchemaName())) {
				builder.name(schemaName.getSchemaName());
				return true;
			}
		}
		return false;
	}

	private Options options;

	@Override
	public Options getOptions() {
		if (options == null) {
			return this.getSqlFactoryRegistry()
					.getOption();
		}
		return options;
	}

	@Override
	public void setOptions(Options option) {
		this.options = option;
	}

	protected void addSql(List<SqlOperation> sqlList,
			SqlOperation sqlOperation) {
		sqlList.add(sqlOperation);
	}
	
	protected void addSql(final List<SqlOperation> sqlList,
			AbstractSqlBuilder<?> builder, SqlType sqlType, DbCommonObject<?> original) {
		if (builder==null) {
			return;
		}
		String sql = builder.toString();
		if (CommonUtils.isEmpty(sql)) {
			return;
		}
		sqlList.add(createOperation(sql, sqlType, original));
	}
	
	protected void addSql(List<SqlOperation> sqlList,
			AbstractSqlBuilder<?> builder, SqlType sqlType, List<? extends DbCommonObject<?>> originals) {
		if (builder==null) {
			return;
		}
		final String sql = builder.toString();
		if (CommonUtils.isEmpty(sql)) {
			return;
		}
		if (originals.size()==1){
			sqlList.add(createOperation(sql, sqlType, originals.get(0)));
		} else{
			sqlList.add(createOperation(sql, sqlType, originals));
		}
	}

	protected SqlOperation createOperation(String text, SqlType sqlType, DbCommonObject<?> original, DbCommonObject<?> target) {
		SqlOperation operation = new SqlOperation(text, sqlType, original, target);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(String text, SqlType sqlType, DbCommonObject<?> original) {
		SqlOperation operation = new SqlOperation(text, sqlType, original);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(String text, SqlType sqlType, List<? extends DbCommonObject<?>> originals) {
		SqlOperation operation = new SqlOperation(text, sqlType, originals);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(String text, SqlType sqlType, List<? extends DbCommonObject<?>> originals, List<? extends DbCommonObject<?>> targets) {
		SqlOperation operation = new SqlOperation(text, sqlType, originals, targets);
		initialize(operation);
		return operation;
	}

	protected void initialize(SqlOperation operation){
		this.getDialect().setChangeAndResetSqlDelimiter(operation);
	}
	
	/**
	 * 楽観的ロックの対象カラムか?
	 */
	protected boolean isOptimisticLockColumn(Column column){
		TableOptions option=this.getOptions().getTableOptions();
		if (CommonUtils.isEmpty(option.getOptimisticLockColumn())){
			return this.getDialect().isOptimisticLockColumn(column);
		}
		return option.getOptimisticLockColumn().test(column);
	}

	/**
	 * 楽観的ロックカラムの更新時の定義を取得します。
	 * @param column
	 */
	protected String getOptimisticLockColumnUpdateDefinition(Column column){
		return getOptimisticLockColumnUpdateDefinition(column, 1);
	}
	
	/**
	 * 楽観的ロックカラムの更新時の定義を取得します。
	 * @param column
	 */
	protected String getOptimisticLockColumnUpdateDefinition(Column column, Integer increment){
		if (column.getDataType().isNumeric()){
			if (column.isNotNull()){
				if (increment!=null){
					return getQuoteName(column.getName())+" + "+increment;
				} else{
					return getQuoteName(column.getName());
				}
			} else{
				if (!withCoalesceAtUpdate(column)){
					if (increment!=null){
						return getQuoteName(column.getName())+" + "+increment;
					} else{
						return getQuoteName(column.getName());
					}
				} else{
					StringBuilder builder=new StringBuilder();
					builder.append("COALESCE( ");
					builder.append(getQuoteName(column.getName()));
					builder.append(", ");
					if (CommonUtils.isEmpty(column.getDefaultValue())){
						builder.append("0");
					} else{
						builder.append(column.getDefaultValue());
					}
					builder.append(" )");
					if (increment!=null){
						builder.append(" + ");
						builder.append(increment);
					}
					return builder.toString();
				}
			}
		}
		return null;
	}
	
	/**
	 * 楽観的ロックカラムの更新時の定義を取得します。
	 * @param column
	 */
	protected String getOptimisticLockColumnCondition(Column column){
		if (column.getDataType().isNumeric()){
			StringBuilder builder=new StringBuilder();
			String value=getValueDefinitionSimple(column);
			builder.append("COALESCE( ");
			builder.append(value);
			builder.append(", ");
			builder.append(getQuoteName(column.getName()));
			builder.append(", ");
			if (CommonUtils.isEmpty(column.getDefaultValue())){
				builder.append("0");
			} else{
				builder.append(column.getDefaultValue());
			}
			builder.append(" )");
			return builder.toString();
		} else if (column.getDataType().isBinary()){
			return getQuoteName(column.getName());
		}
		return null;
	}
	
	/**
	 * クォートされた名前を返します。
	 * 
	 * @param name
	 */
	protected String getQuoteName(String name) {
		if (getDialect() != null && getDialect().needQuote(name)) {
			if (this.isQuateColumnName()) {
				return getDialect().quote(name);
			} else {
				return name;
			}
		}
		return name;
	}
	
	/**
	 * 作成日時カラムか?
	 */
	protected boolean isCreatedAtColumn(Column column){
		TableOptions option=this.getOptions().getTableOptions();
		return option.getCreatedAtColumn().test(column);
	}

	/**
	 * 更新日時カラムか?
	 */
	protected boolean isUpdatedAtColumn(Column column){
		TableOptions option=this.getOptions().getTableOptions();
		return option.getUpdatedAtColumn().test(column);
	}

	/**
	 * Auto Incrementカラムか?
	 */
	protected boolean isAutoIncrementColumn(Column column){
		TableOptions option=this.getOptions().getTableOptions();
		return option.getAutoIncrementColumn().test(column);
	}

	/**
	 * 現在日時の定義を取得します。
	 * @param column
	 * @param builder
	 */
	protected String getCurrentDateDefinition(Column column){
		if (!column.getDataType().isDateTime()){
			return null;
		}
		DbDataType<?> dbDataType=this.getDialect().getDbDataType(column);
		return dbDataType.getDefaultValueLiteral();
	}
	
	protected boolean withCoalesceAtInsert(Column column){
		return this.getOptions().getTableOptions().getWithCoalesceAtInsert().test(column);
	}

	protected boolean withCoalesceAtUpdate(Column column){
		return this.getOptions().getTableOptions().getWithCoalesceAtUpdate().test(column);
	}

	protected String getValueDefinitionForInsert(Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		String columnDefault=column.getDefaultValue();
		String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		if (this.isAutoIncrementColumn(column)){
			return this.getDialect().getIdentityInsertString();
		}else if (isOptimisticLockColumn(column)){
			return _default;
		}
		if (_default == null) {
			return "/*"+column.getName()+"*/1";
		} else {
			return "/*"+column.getName()+"*/"+_default;
		}
	}
	
	protected String getValueDefinitionSimple(Column column) {
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		String columnDefault=column.getDefaultValue();
		String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		if (_default == null) {
			return "/*"+column.getName()+"*/1";
		} else {
			return "/*"+column.getName()+"*/"+_default;
		}
	}
	
	private String getCoalesceValueDefinition(String name, String columnDefault, String typeDefault){
		if (CommonUtils.isEmpty(typeDefault)){
			return "/*"+name+"*/"+columnDefault;
		} else{
			return "COALESCE(/*"+name+"*/"+columnDefault+", "+typeDefault+")";
		}
	}
	
	protected String getValueDefinitionForUpdate(Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		String columnDefault=column.getDefaultValue();
		String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		if (this.isAutoIncrementColumn(column)){
			return null;
		} else if (isCreatedAtColumn(column)){
			return null;
		} else if (isUpdatedAtColumn(column)){
			if (!withCoalesceAtUpdate(column)&&!CommonUtils.isEmpty(dbTypeDefault)) {
				return dbTypeDefault;
			} else {
				return getCoalesceValueDefinition(column.getName(), _default, dbTypeDefault);
			}
		}else if (isOptimisticLockColumn(column)){
			return this.getOptimisticLockColumnUpdateDefinition(column);
		}
		if (_default == null) {
			return "/*"+column.getName()+"*/1";
		} else {
			return "/*"+column.getName()+"*/"+_default;
		}
	}

	protected String getDefaultValueDefinition(Column column){
		DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		String columnDefault=column.getDefaultValue();
		return columnDefault!=null?columnDefault:dbTypeDefault;
	}
	
	/**
	 * INSERT用に値の定義を返します。
	 * @param row
	 * @param column
	 */
	protected String getValueDefinitionForInsert(Row row, Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		String columnDefault=column.getDefaultValue();
		Object value=row.get(column);
		TableOptions tableOption=this.getOptions().getTableOptions();
		if (this.isAutoIncrementColumn(column)){
			if (value==null){
				return tableOption.getInsertRowSqlValue().apply(row, column, this.getDialect().getIdentityInsertString());
			}
		} else if (isUpdatedAtColumn(column)){
			if (value==null){
				return tableOption.getInsertRowSqlValue().apply(row, column, this.getCurrentDateDefinition(column));
			}
		} else if (isCreatedAtColumn(column)){
			if (value==null){
				return tableOption.getInsertRowSqlValue().apply(row, column, this.getCurrentDateDefinition(column));
			}
		}else if (isOptimisticLockColumn(column)){
			if (value==null){
				return tableOption.getInsertRowSqlValue().apply(row, column, columnDefault);
			}
		}
		if (value instanceof String &&tableOption.getDynamicValue().test((String)value)){
			return (String)value;
		}
		return tableOption.getInsertRowSqlValue().apply(row, column, this.getDialect().getSqlValueDefinition(column, value));
	}

	/**
	 * UPDATE用に値の定義を返します。
	 * @param row
	 * @param column
	 */
	protected String getValueDefinitionForUpdate(Row row, Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		Object value=row.get(column);
		TableOptions tableOption=this.getOptions().getTableOptions();
		if (this.isAutoIncrementColumn(column)){
			if (value==null){
				return tableOption.getUpdateRowSqlValue().apply(row, column, null);
			}
		}else if (isOptimisticLockColumn(column)){
			return tableOption.getUpdateRowSqlValue().apply(row, column, this.getOptimisticLockColumnUpdateDefinition(column));
		} else if (isUpdatedAtColumn(column)){
			return tableOption.getUpdateRowSqlValue().apply(row, column, this.getCurrentDateDefinition(column));
		} else if (isCreatedAtColumn(column)){
		}
		if (value instanceof String &&tableOption.getDynamicValue().test((String)value)){
			return (String)value;
		}
		return tableOption.getUpdateRowSqlValue().apply(row, column, this.getDialect().getSqlValueDefinition(column, value));
	}

	/**
	 * 検索条件用に値の定義を返します。
	 * @param row
	 * @param column
	 */
	protected String getValueDefinitionForCondition(Row row, Column column) {
		Object value=row.get(column);
		if (value==null){
			return "IS NULL";
		}
		return this.getDialect().getSqlValueDefinition(column, value);
	}
	
	protected S createSqlBuilder(Dialect dialect) {
		S builder = newSqlBuilder(dialect);
		builder.setQuateObjectName(this.isQuateObjectName());
		builder.setQuateColumnName(this.isQuateColumnName());
		initialize(builder);
		return builder;
	}

	@SuppressWarnings("unchecked")
	protected S newSqlBuilder(Dialect dialect){
		return (S)dialect.createSqlBuilder();
	}
	
	protected Map<String, Difference<?>> getAll(Map<String, Difference<?>> allDiff, String... args){
		Map<String, Difference<?>> result=CommonUtils.map();
		for(String arg:args){
			Difference<?> diff=allDiff.get(arg);
			if (diff!=null){
				result.put(arg, diff);
			}
		}
		return result;
	}
	
	protected boolean isFormulaColumn(FormulaProperty<?> p) {
		if (this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(p.getFormula())) {
			return true;
		}
		return false;
	}
}
