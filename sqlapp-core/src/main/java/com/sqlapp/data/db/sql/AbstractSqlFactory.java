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
	public void setDialect(final Dialect dialect) {
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
			final SqlFactoryRegistry sqlFactoryRegistry) {
		this.sqlFactoryRegistry = sqlFactoryRegistry;
	}

	/**
	 * 文字列のコレクションを改行を含む文字列に変換します
	 * 
	 * @param args
	 *            文字列のコレクション
	 * @return 文字列
	 */
	protected String toString(final Collection<String> args) {
		final StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (final String arg : args) {
			if (!first) {
				builder.append("\n");
			} else {
				first = false;
			}
			builder.append(arg);
		}
		return builder.toString();
	}

	protected void initialize(final AbstractSqlBuilder<?> builder) {
	}

	/**
	 * SQLを取得します
	 * 
	 * @param c
	 */
	@Override
	public List<SqlOperation> createSql(final Collection<T> c) {
		final List<SqlOperation> list = CommonUtils.list();
		final List<T> sorted=sort(CommonUtils.list(c));
		for (final T obj : sorted) {
			final List<SqlOperation> ret = createSql(obj);
			final List<SqlOperation> startList = getStartSqlOperations(obj);
			list.addAll(startList);
			list.addAll(ret);
			final List<SqlOperation> endList = getEndSqlOperations(obj);
			list.addAll(endList);
		}
		return list;
	}

	protected List<T> sort(final List<T> c){
		return c;
	}

	protected void add(final List<SqlOperation> list, final SqlOperation... operations){
		if (CommonUtils.isEmpty(operations)){
			return;
		}
		for(final SqlOperation operation:operations){
			if (operation!=null&&!CommonUtils.isEmpty(operation.getSqlText())){
				list.add(operation);
			}
		}
	}
	
	@Override
	public List<SqlOperation> createDiffSql(
			final Collection<DbObjectDifference> differenceCollection) {
		final List<SqlOperation> result = CommonUtils.list();
		List<DbObjectDifference> diffrences = DbObjectDifferenceCollection
				.getByStates(differenceCollection, State.Deleted);
		if (!CommonUtils.isEmpty(diffrences)) {
			final SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (final DbObjectDifference dbObjectDifference : diffrences) {
				final List<SqlOperation> operations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(operations);
			}
		}
		diffrences = DbObjectDifferenceCollection.getByStates(
				differenceCollection, State.Added);
		if (!CommonUtils.isEmpty(diffrences)) {
			final SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (final DbObjectDifference dbObjectDifference : diffrences) {
				final List<SqlOperation> operations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(operations);
			}
		}
		diffrences = DbObjectDifferenceCollection.getByStates(
				differenceCollection, State.Modified);
		if (!CommonUtils.isEmpty(diffrences)) {
			final SqlFactory<?> sqlFactory = this.getSqlFactoryRegistry()
					.getSqlFactory(CommonUtils.first(diffrences));
			diffrences=sort(sqlFactory, diffrences);
			for (final DbObjectDifference dbObjectDifference : diffrences) {
				final List<SqlOperation> dbOperations = sqlFactory
						.createDiffSql(dbObjectDifference);
				result.addAll(dbOperations);
			}
		}
		return result;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected List<DbObjectDifference> sort(final SqlFactory<?> sqlFactory, final List<DbObjectDifference> list){
		if (sqlFactory instanceof AbstractSqlFactory){
			return ((AbstractSqlFactory)sqlFactory).sortDbObjectDifference(list);
		}
		return list;
	}

	protected List<DbObjectDifference> sortDbObjectDifference(final List<DbObjectDifference> list) {
		return list;
	}

	/* (non-Javadoc)
	 * @see com.sqlapp.data.db.sql.SqlFactory#createDiffSql(com.sqlapp.data.schemas.DbObjectDifference)
	 */
	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference obj) {
		return this.createSql(getObject(obj));
	}

	@SuppressWarnings("unchecked")
	private T getObject(final DbObjectDifference obj) {
		if (obj.getState() == State.Deleted) {
			return (T)obj.getOriginal();
		}
		return (T)obj.getTarget();
	}

	@SuppressWarnings("unchecked")
	protected List<SqlOperation> getStartSqlOperations(final T obj) {
		return Collections.EMPTY_LIST;
	}

	@SuppressWarnings("unchecked")
	protected List<SqlOperation> getEndSqlOperations(final T obj) {
		return Collections.EMPTY_LIST;
	}

	protected boolean addSchemaName(final DbObject<?> object,
			final AbstractSqlBuilder<?> builder) {
		if (object instanceof SchemaNameProperty) {
			final SchemaNameProperty<?> schemaName = (SchemaNameProperty<?>) object;
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
	public void setOptions(final Options option) {
		this.options = option;
	}

	protected void addSql(final List<SqlOperation> sqlList,
			final SqlOperation sqlOperation) {
		sqlList.add(sqlOperation);
	}
	
	protected void addSql(final List<SqlOperation> sqlList,
			final AbstractSqlBuilder<?> builder, final SqlType sqlType, final DbCommonObject<?> original) {
		if (builder==null) {
			return;
		}
		final boolean bool=isTargetSql(sqlType, original);
		if (!bool) {
			return;
		}
		final String sql = builder.toString();
		if (CommonUtils.isEmpty(sql)) {
			return;
		}
		sqlList.add(createOperation(sql, sqlType, original));
	}
	
	
	private boolean isTargetSql(final SqlType sqlType, final DbCommonObject<?> original) {
		if (sqlType.isComment()) {
			final boolean bool=this.getOptions().getOutputCommit().test(original);
			return bool;
		}
		return true;
	}

	protected void addSql(final List<SqlOperation> sqlList,
			final AbstractSqlBuilder<?> builder, final SqlType sqlType, final List<? extends DbCommonObject<?>> originals) {
		if (builder==null) {
			return;
		}
		final String sql = builder.toString();
		if (CommonUtils.isEmpty(sql)) {
			return;
		}
		if (originals.size()==1){
			final boolean bool=isTargetSql(sqlType, originals.get(0));
			if (!bool) {
				return;
			}
			sqlList.add(createOperation(sql, sqlType, originals.get(0)));
		} else{
			sqlList.add(createOperation(sql, sqlType, originals));
		}
	}

	protected SqlOperation createOperation(final String text, final SqlType sqlType, final DbCommonObject<?> original, final DbCommonObject<?> target) {
		final SqlOperation operation = new SqlOperation(text, sqlType, original, target);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(final String text, final SqlType sqlType, final DbCommonObject<?> original) {
		final SqlOperation operation = new SqlOperation(text, sqlType, original);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(final String text, final SqlType sqlType, final List<? extends DbCommonObject<?>> originals) {
		final SqlOperation operation = new SqlOperation(text, sqlType, originals);
		initialize(operation);
		return operation;
	}

	protected SqlOperation createOperation(final String text, final SqlType sqlType, final List<? extends DbCommonObject<?>> originals, final List<? extends DbCommonObject<?>> targets) {
		final SqlOperation operation = new SqlOperation(text, sqlType, originals, targets);
		initialize(operation);
		return operation;
	}

	protected void initialize(final SqlOperation operation){
		this.getDialect().setChangeAndResetSqlDelimiter(operation);
	}
	
	/**
	 * 楽観的ロックの対象カラムか?
	 * @param column Column
	 */
	protected boolean isOptimisticLockColumn(final Column column){
		final TableOptions option=this.getOptions().getTableOptions();
		if (CommonUtils.isEmpty(option.getOptimisticLockColumn())){
			return this.getDialect().isOptimisticLockColumn(column);
		}
		return option.getOptimisticLockColumn().test(column);
	}

	/**
	 * 楽観的ロックカラムの更新時の定義を取得します。
	 * @param column
	 */
	protected String getOptimisticLockColumnUpdateDefinition(final String prefix, final Column column){
		return getOptimisticLockColumnUpdateDefinition(prefix, column, 1);
	}
	
	/**
	 * 楽観的ロックカラムの更新時の定義を取得します。
	 * @param column
	 */
	protected String getOptimisticLockColumnUpdateDefinition(final String prefix, final Column column, final Integer increment){
		if (column.getDataType().isNumeric()){
			if (column.isNotNull()){
				if (increment!=null){
					return combine(prefix, getQuoteName(column))+" + "+increment;
				} else{
					return combine(prefix, getQuoteName(column));
				}
			} else{
				if (!withCoalesceAtUpdate(column)){
					if (increment!=null){
						return combine(prefix, getQuoteName(column))+" + "+increment;
					} else{
						return combine(prefix, getQuoteName(column));
					}
				} else{
					final StringBuilder builder=new StringBuilder();
					builder.append("COALESCE( ");
					builder.append(combine(prefix, getQuoteName(column)));
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
	 * @param column Column
	 */
	protected String getOptimisticLockColumnCondition(final Column column){
		if (column.getDataType().isNumeric()){
			final StringBuilder builder=new StringBuilder();
			final String value=getValueDefinitionSimple(column);
			builder.append("COALESCE( ");
			builder.append(value);
			builder.append(", ");
			builder.append(getQuoteName(column));
			builder.append(", ");
			if (CommonUtils.isEmpty(column.getDefaultValue())){
				builder.append("0");
			} else{
				builder.append(column.getDefaultValue());
			}
			builder.append(" )");
			return builder.toString();
		} else if (column.getDataType().isBinary()){
			return getQuoteName(column);
		}
		return null;
	}
	
	/**
	 * クォートされた名前を返します。
	 * 
	 * @param column Column
	 * @return クォートされた名前
	 */
	protected String getQuoteName(final Column column) {
		if (getDialect() != null && getDialect().needQuote(column.getName())) {
			if (this.getOptions().isQuateColumnName()) {
				return getDialect().quote(column.getName());
			} else {
				return column.getName();
			}
		}
		return column.getName();
	}

	/**
	 * クォートされた名前を返します。
	 * 
	 * @param prefix プレフィックス
	 * @param column Column
	 */
	protected String getQuoteName(final String prefix, final Column column) {
		if (getDialect() != null && getDialect().needQuote(column.getName())) {
			if (this.getOptions().isQuateColumnName()) {
				return combine(prefix, getDialect().quote(column.getName()));
			} else {
				return combine(prefix, column.getName());
			}
		}
		return combine(prefix, column.getName());
	}
	
	private String combine(final String text1, final String text2) {
		if (text1==null) {
			return text2;
		}
		if (text2==null) {
			return text1;
		}
		return text1+text2;
	}
	
	/**
	 * 作成日時カラムか?
	 * @param column Column
	 */
	protected boolean isCreatedAtColumn(final Column column){
		final TableOptions option=this.getOptions().getTableOptions();
		return option.getCreatedAtColumn().test(column);
	}

	/**
	 * 更新日時カラムか?
	 * @param column Column
	 */
	protected boolean isUpdatedAtColumn(final Column column){
		final TableOptions option=this.getOptions().getTableOptions();
		return option.getUpdatedAtColumn().test(column);
	}

	/**
	 * Auto Incrementカラムか?
	 */
	protected boolean isAutoIncrementColumn(final Column column){
		final TableOptions option=this.getOptions().getTableOptions();
		return option.getAutoIncrementColumn().test(column);
	}

	/**
	 * 現在日時の定義を取得します。
	 * @param column
	 * @param builder
	 */
	protected String getCurrentDateDefinition(final Column column){
		if (!column.getDataType().isDateTime()){
			return null;
		}
		final DbDataType<?> dbDataType=this.getDialect().getDbDataType(column);
		return dbDataType.getDefaultValueLiteral();
	}
	
	protected boolean withCoalesceAtInsert(final Column column){
		return this.getOptions().getTableOptions().getWithCoalesceAtInsert().test(column);
	}

	protected boolean withCoalesceAtUpdate(final Column column){
		return this.getOptions().getTableOptions().getWithCoalesceAtUpdate().test(column);
	}

	protected String getValueDefinitionForInsert(final Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		final DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		final String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		final String columnDefault=column.getDefaultValue();
		final String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		if (this.isAutoIncrementColumn(column)){
			return this.getDialect().getIdentityInsertString();
		}else if (isOptimisticLockColumn(column)){
			return _default;
		} else if (isCreatedAtColumn(column)){
			if (!withCoalesceAtInsert(column)&&!CommonUtils.isEmpty(dbTypeDefault)) {
				return dbTypeDefault;
			} else {
				return getCoalesceValueDefinition(column, _default, dbTypeDefault);
			}
		} else if (isUpdatedAtColumn(column)){
			if (!withCoalesceAtUpdate(column)&&!CommonUtils.isEmpty(dbTypeDefault)) {
				return dbTypeDefault;
			} else {
				return getCoalesceValueDefinition(column, _default, dbTypeDefault);
			}
		}
		return getColumnParameterExpression(column, _default);
	}
	
	protected String getValueDefinitionSimple(final Column column) {
		final DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		final String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		final String columnDefault=column.getDefaultValue();
		final String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		return getColumnParameterExpression(column, _default);
	}
	
	private String getColumnParameterExpression(final Column column, final String _default) {
		if (this.getOptions()!=null) {
			if (this.getOptions().getTableOptions()!=null) {
				if (this.getOptions().getTableOptions().getParameterExpression()!=null) {
					return this.getOptions().getTableOptions().getParameterExpression().apply(column, _default);
				}
			}
		}
		if (_default == null) {
			return "/*"+column.getName()+"*/1";
		} else {
			if (_default.contains("(")) {
				return "/*"+column.getName()+"*/''";
			}
			return "/*"+column.getName()+"*/"+_default;
		}
	}
	
	private String getCoalesceValueDefinition(final Column column, final String columnDefault, final String typeDefault){
		if (CommonUtils.isEmpty(typeDefault)){
			return getColumnParameterExpression(column, columnDefault);
		} else{
			return "COALESCE("+getColumnParameterExpression(column, columnDefault)+", "+typeDefault+")";
		}
	}
	
	protected String getValueDefinitionForUpdate(final Column column) {
		return getValueDefinitionForUpdate((String)null, column);
	}

	protected String getValueDefinitionForUpdate(final String prefix, final Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		final DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		final String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		final String columnDefault=column.getDefaultValue();
		final String _default=CommonUtils.coalesce(columnDefault, dbTypeDefault);
		if (this.isAutoIncrementColumn(column)){
			return null;
		} else if (isCreatedAtColumn(column)){
			return null;
		} else if (isUpdatedAtColumn(column)){
			if (!withCoalesceAtUpdate(column)&&!CommonUtils.isEmpty(dbTypeDefault)) {
				return dbTypeDefault;
			} else {
				return getCoalesceValueDefinition(column, _default, dbTypeDefault);
			}
		}else if (isOptimisticLockColumn(column)){
			return this.getOptimisticLockColumnUpdateDefinition(prefix, column);
		}
		return getColumnParameterExpression(column, _default);
	}

	
	protected String getDefaultValueDefinition(final Column column){
		final DbDataType<?> dbDataType = this.getDialect().getDbDataType(column);
		final String dbTypeDefault=dbDataType.getDefaultValueLiteral();
		final String columnDefault=column.getDefaultValue();
		return columnDefault!=null?columnDefault:dbTypeDefault;
	}
	
	/**
	 * INSERT用に値の定義を返します。
	 * @param row Row
	 * @param column Column
	 * @return UPDATE条件用の値の定義
	 */
	protected String getValueDefinitionForInsert(final Row row, final Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		final String columnDefault=column.getDefaultValue();
		final Object value=row.get(column);
		final TableOptions tableOption=this.getOptions().getTableOptions();
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
	 * @param row Row
	 * @param column Column
	 * @return UPDATE条件用の値の定義
	 */
	protected String getValueDefinitionForUpdate(final Row row, final Column column) {
		if (this.isFormulaColumn(column)) {
			return null;
		}
		if (!this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(column.getFormula())) {
			return column.getFormula();
		}
		final Object value=row.get(column);
		final TableOptions tableOption=this.getOptions().getTableOptions();
		if (this.isAutoIncrementColumn(column)){
			if (value==null){
				return tableOption.getUpdateRowSqlValue().apply(row, column, null);
			}
		}else if (isOptimisticLockColumn(column)){
			return tableOption.getUpdateRowSqlValue().apply(row, column, this.getOptimisticLockColumnUpdateDefinition(null, column));
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
	 * @param row Row
	 * @param column Column
	 * @return 検索条件用の値の定義
	 */
	protected String getValueDefinitionForCondition(final Row row, final Column column) {
		final Object value=row.get(column);
		if (value==null){
			return "IS NULL";
		}
		return this.getDialect().getSqlValueDefinition(column, value);
	}
	
	protected S createSqlBuilder(final Dialect dialect) {
		final S builder = newSqlBuilder(dialect);
		builder.setQuateObjectName(this.getOptions().isQuateObjectName());
		builder.setQuateColumnName(this.getOptions().isQuateColumnName());
		initialize(builder);
		return builder;
	}

	@SuppressWarnings("unchecked")
	protected S newSqlBuilder(final Dialect dialect){
		return (S)dialect.createSqlBuilder();
	}
	
	protected Map<String, Difference<?>> getAll(final Map<String, Difference<?>> allDiff, final String... args){
		final Map<String, Difference<?>> result=CommonUtils.map();
		for(final String arg:args){
			final Difference<?> diff=allDiff.get(arg);
			if (diff!=null){
				result.put(arg, diff);
			}
		}
		return result;
	}
	
	protected boolean isFormulaColumn(final FormulaProperty<?> p) {
		if (this.getDialect().supportsColumnFormula()&&!CommonUtils.isEmpty(p.getFormula())) {
			return true;
		}
		return false;
	}
}
