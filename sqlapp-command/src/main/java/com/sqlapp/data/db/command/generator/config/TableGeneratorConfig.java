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

package com.sqlapp.data.db.command.generator.config;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.exceptions.InvalidExpressionResultTypeException;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorConfigFactory;
import com.sqlapp.data.db.command.generator.util.CachedMvelEvaluatorUtils;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.CachedEvaluator;

import lombok.Getter;
import lombok.Setter;

/**
 * テーブルデータ生成設定
 */
@Getter
@Setter
public class TableGeneratorConfig {
	/** Schema Name */
	@JsonProperty(index = 0)
	private String schemaName;
	/** Table Name */
	@JsonProperty(index = 1)
	private String name;
	/** Setup SQL */
	@JsonProperty(index = 2)
	private String startCountSql;
	/** Start Value SQL */
	@JsonProperty(index = 3)
	private String dataSourceExpression;
	/** data mapping */
	@JsonProperty(index = 4)
	private String columnMappingExpression;
	/** Start Value SQL */
	@JsonProperty(index = 5)
	private String startValueSql;
	/** Setup SQL */
	@JsonProperty(index = 6)
	private String initializeSql;
	/** Insert SQL */
	@JsonProperty(index = 7)
	private String insertSql;
	/** Finalize SQL */
	@JsonProperty(index = 8)
	private String finalizeSql;
	/** Setup SQL */
	@JsonProperty(index = 9)
	private String finishCountSql;
	@JsonProperty(index = 10)
	private Map<String, ColumnGeneratorConfig> columns = CommonUtils.caseInsensitiveLinkedMap();
	@JsonProperty(index = 11)
	private Map<String, QueryGeneratorConfig> queries = new LinkedHashMap<>();
	@JsonProperty(index = 12)
	private Map<String, FileGeneratorConfig> files = new LinkedHashMap<>();
	@JsonIgnore
	private CachedEvaluator evaluator = CachedMvelEvaluatorUtils.getCachedMvelEvaluator();
	@JsonIgnore
	private List<ColumnGeneratorConfig> formatColumns = CommonUtils.list();

	@JsonIgnore
	private final ParametersContext startValues = new ParametersContext();
	@JsonIgnore
	private final ParametersContext minValues = new ParametersContext();
	@JsonIgnore
	private final ParametersContext maxValues = new ParametersContext();
	@JsonIgnore
	private Map<String, Object> previousValues = Collections.emptyMap();
	@JsonIgnore
	private Table table;
	@JsonIgnore
	private File file;
	@JsonIgnore
	private ConfigFileType fileType;

	public void clear() {
		name = null;
		initializeSql = null;
		startValueSql = null;
		insertSql = null;
		finalizeSql = null;
		columns = null;
		queries = null;
		previousValues = null;
		formatColumns = null;
		table = null;
	}

	/**
	 * 現在のコンテキストの値の「キー
	 */
	@JsonIgnore
	public static final String VALUE = "_value";
	/**
	 * 最小値参照時のキー
	 */
	@JsonIgnore
	public static final String MIN_KEY = "_min";
	/**
	 * 最大値参照時のキー
	 */
	@JsonIgnore
	public static final String MAX_KEY = "_max";
	/**
	 * インデックス参照時のキー
	 */
	@JsonIgnore
	public static final String INDEX_KEY = "_index";
	/**
	 * インデックス参照時のキー
	 */
	@JsonIgnore
	public static final String ROW_NO_KEY = "_row_number";
	/**
	 * 前の値参照時のキー
	 */
	@JsonIgnore
	public static final String PREVIOUS_KEY = "_previous";

	@JsonIgnore
	private long generateCount = 0;

	public void addColumn(ColumnGeneratorConfig col) {
		if (!CommonUtils.isBlank(col.getFormatExpression())) {
			formatColumns.add(col);
		}
		columns.put(col.getName(), col);
	}

	public void addQueryDefinition(QueryGeneratorConfig obj) {
		queries.put(obj.getGenerationGroup(), obj);
		obj.setTableGeneratorConfig(this);
	}

	public void addFileDefinition(FileGeneratorConfig obj) {
		files.put(obj.getLookupGroup(), obj);
		obj.setTableGeneratorConfig(this);
	}

	@JsonIgnore
	public File getParentDirectory() {
		return this.getFile().getParentFile();
	}

	/**
	 * 値のチェックを行います。
	 */
	public void check() {
		columns.entrySet().forEach(entry -> {
			final String genGroup = entry.getValue().getLookupGroup();
			if (!CommonUtils.isEmpty(genGroup)) {
				final QueryGeneratorConfig queryDef = queries.get(genGroup);
				if (queryDef != null) {
					entry.getValue().setQueryGeneratorConfig(queryDef);
				}
				final FileGeneratorConfig fileDef = files.get(genGroup);
				if (fileDef != null) {
					entry.getValue().setFileGeneratorConfig(fileDef);
				}
			}
		});
	}

	/**
	 * 初期値を評価します
	 * 
	 */
	public synchronized void calculateInitialObjectValues() {
		int i = 1;
		for (Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final ColumnGeneratorConfig colConfig = entry.getValue();
			final String expression = colConfig.getMinValue();
			i++;
			if (!CommonUtils.isEmpty(expression)) {
				try {
					Object value = eval(expression, Collections.emptyMap());
					colConfig.setMinValueObject(value);
					colConfig.setStartValueObject(value);
					minValues.put(colConfig.getName(), value);
					startValues.put(colConfig.getName(), value);
				} catch (RuntimeException e) {
					throw new ExpressionExecutionException(
							"Column Min Value expression is invalid. column=[F" + i + "]", e);
				}
			}
		}
		minValues.remove(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME);
		startValues.remove(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME);
		final Map<String, Object> map = CommonUtils.map();
		map.put(MIN_KEY, minValues);
		i = 1;
		for (Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final ColumnGeneratorConfig colConfig = entry.getValue();
			String expression = colConfig.getMaxValue();
			i++;
			if (!CommonUtils.isEmpty(expression)) {
				try {
					Object value = eval(expression, map);
					colConfig.setMaxValueObject(value);
					maxValues.put(colConfig.getName(), value);
				} catch (RuntimeException e) {
					throw new ExpressionExecutionException(
							"Column Max Value expression is invalid. column=[G" + i + "]", e);
				}
			}
		}
		maxValues.remove(ParameterDefinition.COUNTSQL_KEY_PARANETER_NAME);
	}

	@SuppressWarnings("unchecked")
	protected <S> S eval(String expression) {
		return (S) evaluator.eval(expression, Collections.emptyMap());
	}

	@SuppressWarnings("unchecked")
	protected <S> S eval(String expression, Object arg) {
		return (S) evaluator.eval(expression, arg);
	}

	public void setSqlStartValue(final Map<String, Object> map) {
		startValues.putAll(this.getPreviousValues());
		for (Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final ColumnGeneratorConfig colGen = entry.getValue();
			if (CommonUtils.isEmpty(colGen.getLookupGroup()) && colGen.isPrimaryKeyAndForeignKeyColumn()) {
				final Object obj = map.get(entry.getKey());
				if (obj != null) {
					startValues.put(entry.getKey(), obj);
				}
			}
		}
		for (final Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final String key = entry.getKey();
			final ColumnGeneratorConfig colConfig = columns.get(key);
			final Object obj = map.get(key);
			if (obj == null) {
				continue;
			}
			if (colConfig.isPrimaryKeyOrIdentityColumn()) {
				// PKもしくはIDENTITYの場合はインクリメントを継続する
				continue;
			}
			if (colConfig.getMinValueObject() == null) {
				if (colConfig.getMaxValueObject() == null) {
					continue;
				} else {
					final Object converted = Converters.getDefault().convertObject(obj,
							colConfig.getMaxValueObject().getClass());
					colConfig.setStartValueObject(converted);
					startValues.put(key, converted);
				}
			} else {
				final Object converted = Converters.getDefault().convertObject(obj,
						colConfig.getMinValueObject().getClass());
				colConfig.setStartValueObject(converted);
				startValues.put(key, converted);
			}
		}
		generateCount = 0;
	}

	/**
	 * 値を生成します
	 * 
	 * @param rowNumber 行順番
	 * @param index     生成順番
	 * @return 生成した値
	 */
	public Map<String, Object> generateValue(long rowNumber, long index) {
		final Map<String, Object> map = CommonUtils.linkedMap();
		map.put(ROW_NO_KEY, rowNumber);
		map.put(INDEX_KEY, index);
		previousValues.remove(PREVIOUS_KEY);
		previousValues.remove(MIN_KEY);
		previousValues.remove(MAX_KEY);
		map.put(MIN_KEY, minValues);
		map.put(MAX_KEY, maxValues);
		if (generateCount == 0) {
			map.put(PREVIOUS_KEY, startValues);
			for (final Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
				final ColumnGeneratorConfig colConfig = entry.getValue();
				Object obj = startValues.get(colConfig.getName());
				if (obj == null) {
					ResultHolder resultHolder = generateInternal(0, colConfig, map);
					if (resultHolder.isPresent()) {
						map.put(colConfig.getName(), resultHolder.get());
					}
				} else {
					map.put(colConfig.getName(), obj);
				}
			}
		} else {
			map.put(PREVIOUS_KEY, previousValues);
			generateInternal(index, map);
		}
		previousValues = map;
		final Map<String, Object> copy = CommonUtils.linkedMap(map);
		convertFormat(copy);
		generateCount++;
		return copy;
	}

	private void generateInternal(long rowNumber, final Map<String, Object> map) {
		for (final Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final ColumnGeneratorConfig colConfig = entry.getValue();
			ResultHolder resultHolder = generateInternal(rowNumber, colConfig, map);
			if (resultHolder.isPresent()) {
				map.put(colConfig.getName(), resultHolder.get());
			}
		}
	}

	static class ResultHolder {
		private ResultHolder(Object value) {
			this.value = value;
		}

		private static final ResultHolder EMPTY = new ResultHolder(null) {
			@Override
			public boolean isPresent() {
				return false;
			}
		};

		public static ResultHolder Of(Object value) {
			return new ResultHolder(value);
		}

		public static ResultHolder Of(Optional<?> value) {
			return new ResultHolder(value.get());
		}

		@SuppressWarnings("unchecked")
		public <T> T get() {
			return (T) value;
		}

		public boolean isPresent() {
			return true;
		}

		public static ResultHolder empty() {
			return EMPTY;
		}

		private final Object value;
	}

	private ResultHolder generateInternal(long rowNumber, final ColumnGeneratorConfig colConfig,
			final Map<String, Object> map) {
		final int intIndex = (int) (rowNumber % Integer.MAX_VALUE);
		// クエリグループから取得
		if (colConfig.getQueryGeneratorConfig() != null) {
			final Map<String, Object> queryValueMapOptional = colConfig.getQueryGeneratorConfig().getValueMap(intIndex);
			if (queryValueMapOptional != null) {
				return ResultHolder.Of(queryValueMapOptional.get(colConfig.getName()));
			}
			return ResultHolder.empty();
		}
		// ファイルグループから取得
		if (colConfig.getFileGeneratorConfig() != null) {
			final Map<String, Object> valueMap = colConfig.getFileGeneratorConfig().getValueMap(intIndex);
			if (valueMap != null) {
				Object obj = valueMap.get(colConfig.getName());
				return ResultHolder.Of(obj);
			}
			return ResultHolder.empty();
		}
		// Valuesから取得
		final Optional<Object> op = colConfig.getValue(intIndex);
		if (op.isPresent()) {
			return ResultHolder.Of(op);
		}
		if (CommonUtils.isEmpty(colConfig.getNextValue())) {
			return ResultHolder.Of(colConfig.getStartValueObject());
		}
		// Next Valueから取得
		String expression = colConfig.getNextValue();
		Object value;
		try {
			value = eval(expression, map);
		} catch (RuntimeException e) {
			throw new ExpressionExecutionException("Column nextValue expression is invalid. tablName" + this.getName()
					+ ", columnName=" + colConfig.getName() + " expression=expression", e);
		}
		if (colConfig.getMaxValueObject() != null) {
			int comp = compare(colConfig.getMaxValueObject(), value);
			if (comp > 0) {
				return ResultHolder.Of(value);
			} else {
				return ResultHolder.Of(colConfig.getMinValueObject());
			}
		} else {
			return ResultHolder.Of(value);
		}
	}

	private void convertFormat(final Map<String, Object> map) {
		for (final ColumnGeneratorConfig colConfig : this.formatColumns) {
			final Optional<Object> optional = getFormatedValue(colConfig, map);
			if (optional.isPresent()) {
				map.put(colConfig.getName(), optional.get());
			}
		}
	}

	private Optional<Object> getFormatedValue(final ColumnGeneratorConfig colConfig, final Map<String, Object> map) {
		if (CommonUtils.isEmpty(colConfig.getFormatExpression())) {
			return Optional.empty();
		}
		final Map<String, Object> copy = CommonUtils.map(map);
		copy.put(VALUE, map.get(colConfig.getName()));
		final Object formatted = this.getEvaluator().eval(colConfig.getFormatExpression(), copy);
		return Optional.of(formatted);
	}

	public void initializeTableColumnData(final Table table) {
		this.setTable(table);
		ForeignKeyConstraint fk = TableGeneratorConfigFactory.getPKFK(table);
		for (final Map.Entry<String, ColumnGeneratorConfig> entry : columns.entrySet()) {
			final ColumnGeneratorConfig colConfig = entry.getValue();
			final Column column = table.getColumns().get(entry.getKey());
			if (column == null) {
				continue;
			}
			colConfig.setPrimaryKeyAndForeignKeyColumn(TableGeneratorConfigFactory.isPKFKColumn(table, fk, column));
			colConfig.setColumn(column);
			if (column.isIdentity()) {
				colConfig.setPrimaryKeyOrIdentityColumn(true);
			} else if (table.getPrimaryKeyConstraint().getColumns().contains(column.getName())) {
				colConfig.setPrimaryKeyOrIdentityColumn(column.getDataType().isNumeric());
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private int compare(Object o1, Object o2) {
		if (o1 == null) {
			if (o2 == null) {
				return 0;
			} else {
				return -1;
			}
		} else {
			if (o2 == null) {
				return 1;
			}
			if (o1.getClass().equals(o2.getClass())) {
				if (o1 instanceof Comparable) {
					return ((Comparable) o1).compareTo(o2);
				}
			}
			Object o2conv = Converters.getDefault().convertObject(o2, o1.getClass());
			return ((Comparable) o1).compareTo(o2conv);
		}
	}

	/**
	 * DBからデータを読み込みます
	 * 
	 * @param conn DBコネクション
	 * @throws SQLException
	 */
	public void loadData(Connection conn, SQLExceptionConsumer<QueryGeneratorConfig> cons) throws SQLException {
		final Set<String> columnGroups = getColumnGroups();
		long i = 0;
		for (Map.Entry<String, QueryGeneratorConfig> entry : queries.entrySet()) {
			final QueryGeneratorConfig config = entry.getValue();
			if (columnGroups.contains(config.getGenerationGroup())) {
				cons.accept(i++, config);
			}
		}
	}

	/**
	 * ファイルからデータを読み込みます
	 * 
	 */
	public void loadFileData(SQLExceptionConsumer<FileGeneratorConfig> cons) throws SQLException {
		long i = 0;
		final Set<String> columnGroups = getColumnGroups();
		for (Map.Entry<String, FileGeneratorConfig> entry : files.entrySet()) {
			final FileGeneratorConfig config = entry.getValue();
			if (columnGroups.contains(config.getLookupGroup())) {
				cons.accept(i++, config);
			}
		}
	}

	private Set<String> getColumnGroups() {
		return columns.values().stream().map(c -> c.getLookupGroup()).collect(Collectors.toSet());
	}

	public long iterateCount() {
		Map<String, Object> map = CommonUtils.map();
		map.put("schemaName", this.getSchemaName());
		map.put("tableName", this.getName());
		if (CommonUtils.isBlank(getDataSourceExpression())) {
			return 0;
		}
		final Object tmp = this.getEvaluator().eval(this.getDataSourceExpression(), map);
		checkTypeCheck("Table.dataSourceExpression", this.getDataSourceExpression(), tmp, Iterable.class);
		final Iterable<?> iterable = CommonUtils.cast(tmp);
		long i = 0;
		for (Object obj : iterable) {
			if (i == 0) {
				final Object ret = this.getEvaluator().eval(this.getColumnMappingExpression(), obj);
				checkTypeCheck("Table.columnMappingExpression", this.getColumnMappingExpression(), ret, Map.class);
			}
			i++;
		}
		return i;
	}

	private void checkTypeCheck(final String expressionPath, String expression, final Object obj, Class<?> clazz) {
		if (obj == null) {
			throw new InvalidExpressionResultTypeException(expressionPath, expression, null, Iterable.class,
					"Invalid expression result type.");
		}
		if (!(clazz.isAssignableFrom(obj.getClass()))) {
			throw new InvalidExpressionResultTypeException(expressionPath, expression, obj, Iterable.class,
					"Invalid expression result type.");
		}
	}

	@JsonIgnore
	public Iterable<Map<String, Object>> getDataSource() {
		Map<String, Object> map = CommonUtils.map();
		map.put("schemaName", this.getSchemaName());
		map.put("tableName", this.getName());
		final Object tmp = this.getEvaluator().eval(this.getDataSourceExpression(), map);
		checkTypeCheck("Table.dataSourceExpression", this.getDataSourceExpression(), tmp, Iterable.class);
		final Iterable<Map<String, Object>> iterable = CommonUtils.cast(tmp);
		return iterable;
	}

	public Map<String, Object> convertColumnMapping(Map<String, Object> map) {
		if (CommonUtils.isBlank(getColumnMappingExpression())) {
			return map;
		}
		if (map == null) {
			return null;
		}
		Map<String, Object> ret = this.getEvaluator().eval(this.getColumnMappingExpression(), map);
		return ret;
	}

	@FunctionalInterface
	public interface SQLExceptionConsumer<T> {

		/**
		 * Performs this operation on the given argument.
		 *
		 * @param index index
		 * @param t     the input argument
		 * @throws SQLException
		 */
		void accept(long index, T t) throws SQLException;
	}
}
