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

package com.sqlapp.data.db.command.generator.setting;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.data.parameter.ParameterDefinition;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.exceptions.ExpressionExecutionException;
import com.sqlapp.util.CommonUtils;
import com.sqlapp.util.eval.CachedEvaluator;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * テーブルデータ生成設定
 */
@Getter
@Setter
@EqualsAndHashCode(exclude = { "evaluator" })
public class TableGeneratorSetting {
	/** テーブル名 */
	@JsonProperty(index = 0)
	private String name;
	/** Setup SQL */
	@JsonProperty(index = 1)
	private String setupSql;
	/** Start Value SQL */
	@JsonProperty(index = 2)
	private String startValueSql;
	/** 行数 */
	@JsonProperty(index = 3)
	private long numberOfRows;
	/** Insert SQL */
	@JsonProperty(index = 4)
	private String insertSql;
	/** Finalize SQL */
	@JsonProperty(index = 5)
	private String finalizeSql;

	@JsonProperty(index = 6)
	private Map<String, ColumnGeneratorSetting> columns = CommonUtils.caseInsensitiveLinkedMap();

	@JsonProperty(index = 7)
	private Map<String, QueryGeneratorSetting> querys = new LinkedHashMap<>();

	@JsonProperty(index = 8)
	private Map<String, FileGeneratorSetting> files = new LinkedHashMap<>();

	@JsonIgnore
	private CachedEvaluator evaluator;

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
	private GeneratorSettingFileType fileType;

	public void clear() {
		name = null;
		setupSql = null;
		startValueSql = null;
		insertSql = null;
		finalizeSql = null;
		columns = null;
		querys = null;
		previousValues = null;
		table = null;
	}

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

	public void addColumn(ColumnGeneratorSetting col) {
		columns.put(col.getName(), col);
	}

	public void addQueryDefinition(QueryGeneratorSetting obj) {
		querys.put(obj.getGenerationGroup(), obj);
	}

	public void addFileDefinition(FileGeneratorSetting obj) {
		files.put(obj.getGenerationGroup(), obj);
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
			final String genGroup = entry.getValue().getGenerationGroup();
			if (!CommonUtils.isEmpty(genGroup)) {
				final QueryGeneratorSetting queryDef = querys.get(genGroup);
				if (queryDef != null) {
					entry.getValue().setQueryGeneratorSetting(queryDef);
				}
				final FileGeneratorSetting fileDef = files.get(genGroup);
				if (fileDef != null) {
					entry.getValue().setFileGeneratorSetting(fileDef);
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
		for (Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			final ColumnGeneratorSetting colSetting = entry.getValue();
			final String expression = colSetting.getMinValue();
			i++;
			if (!CommonUtils.isEmpty(expression)) {
				try {
					Object value = eval(expression, Collections.emptyMap());
					colSetting.setMinValueObject(value);
					colSetting.setStartValueObject(value);
					minValues.put(colSetting.getName(), value);
					startValues.put(colSetting.getName(), value);
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
		for (Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			final ColumnGeneratorSetting colSetting = entry.getValue();
			String expression = colSetting.getMaxValue();
			i++;
			if (!CommonUtils.isEmpty(expression)) {
				try {
					Object value = eval(expression, map);
					colSetting.setMaxValueObject(value);
					maxValues.put(colSetting.getName(), value);
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

	public void setSqlStartValue(final long index, final Map<String, Object> map) {
		startValues.putAll(this.getPreviousValues());
		for (Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			final ColumnGeneratorSetting colGen = entry.getValue();
			if (CommonUtils.isEmpty(colGen.getGenerationGroup()) && colGen.isPrimaryKeyAndForeignKeyColumn()) {
				final Object obj = map.get(entry.getKey());
				if (obj != null) {
					startValues.put(entry.getKey(), obj);
				}
			}
		}
		for (final Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			final String key = entry.getKey();
			final ColumnGeneratorSetting colSetting = columns.get(key);
			final Object obj = map.get(key);
			if (obj == null) {
				continue;
			}
			if (colSetting.isPrimaryKeyOrIdentityColumn()) {
				// PKもしくはIDENTITYの場合はインクリメントを継続する
				continue;
			}
			if (colSetting.getMinValueObject() == null) {
				if (colSetting.getMaxValueObject() == null) {
					continue;
				} else {
					final Object converted = Converters.getDefault().convertObject(obj,
							colSetting.getMaxValueObject().getClass());
					colSetting.setStartValueObject(converted);
					startValues.put(key, converted);
				}
			} else {
				final Object converted = Converters.getDefault().convertObject(obj,
						colSetting.getMinValueObject().getClass());
				colSetting.setStartValueObject(converted);
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
		} else {
			map.put(PREVIOUS_KEY, previousValues);
		}
		generateInternal(rowNumber, index, map);
		previousValues = map;
		generateCount++;
		return map;
	}

	private void generateInternal(long rowNumber, long index, final Map<String, Object> map) {
		final int intIndex = (int) (rowNumber % Integer.MAX_VALUE);
		int i = 1;
		for (final Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			i++;
			final ColumnGeneratorSetting colSetting = entry.getValue();
			// クエリグループから取得
			if (colSetting.getQueryGeneratorSetting() != null) {
				final Optional<Map<String, Object>> queryValueMapOptional = colSetting.getQueryGeneratorSetting()
						.getValueMap(intIndex);
				if (queryValueMapOptional.isPresent()) {
					map.put(colSetting.getName(), queryValueMapOptional.get().get(colSetting.getName()));
				}
				continue;
			}
			// Valuesから取得
			final Optional<Object> op = colSetting.getValue(intIndex);
			if (op.isPresent()) {
				map.put(colSetting.getName(), op.get());
				continue;
			}
//			if (rowNumber == 0 || CommonUtils.isEmpty(colSetting.getNextValue())) {
			if (CommonUtils.isEmpty(colSetting.getNextValue())) {
				map.put(colSetting.getName(), colSetting.getStartValueObject());
			} else {
				// Next Valueから取得
				String expression = colSetting.getNextValue();
				Object value;
				try {
					value = eval(expression, map);
				} catch (RuntimeException e) {
					throw new ExpressionExecutionException("Column expression is invalid. column=[H" + i + "]", e);
				}
				if (colSetting.getMaxValueObject() != null) {
					int comp = compare(colSetting.getMaxValueObject(), value);
					if (comp > 0) {
						map.put(colSetting.getName(), value);
					} else {
						map.put(colSetting.getName(), colSetting.getMinValueObject());
					}
				} else {
					map.put(colSetting.getName(), value);
				}
			}
		}
	}

	public void initializeTableColumnData(final Table table) {
		this.setTable(table);
		ForeignKeyConstraint fk = TableGeneratorSettingFactory.getPKFK(table);
		for (final Map.Entry<String, ColumnGeneratorSetting> entry : columns.entrySet()) {
			final ColumnGeneratorSetting colSetting = entry.getValue();
			final Column column = table.getColumns().get(entry.getKey());
			if (column == null) {
				continue;
			}
			colSetting.setPrimaryKeyAndForeignKeyColumn(TableGeneratorSettingFactory.isPKFKColumn(table, fk, column));
			colSetting.setColumn(column);
			if (column.isIdentity()) {
				colSetting.setPrimaryKeyOrIdentityColumn(true);
			} else if (table.getPrimaryKeyConstraint().getColumns().contains(column.getName())) {
				colSetting.setPrimaryKeyOrIdentityColumn(column.getDataType().isNumeric());
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
	public void loadData(Connection conn, SQLExceptionConsumer<QueryGeneratorSetting> cons) throws SQLException {
		for (Map.Entry<String, QueryGeneratorSetting> entry : querys.entrySet()) {
			final QueryGeneratorSetting setting = entry.getValue();
			cons.accept(setting);
		}
	}

	@FunctionalInterface
	public interface SQLExceptionConsumer<T> {

		/**
		 * Performs this operation on the given argument.
		 *
		 * @param t the input argument
		 * @throws SQLException
		 */
		void accept(T t) throws SQLException;
	}
}
