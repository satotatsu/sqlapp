package com.sqlapp.data.db.command.generator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.parameter.ParametersContext;
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
@EqualsAndHashCode
public class TableDataGeneratorSetting {
	/** テーブル名 */
	private String name;
	/** 行数 */
	private long numberOfRows;

	private Map<String, ColumnDataGeneratorSetting> columns = CommonUtils.caseInsensitiveLinkedMap();

	private Map<String, QueryDefinitionDataGeneratorSetting> queryDefinitions = new LinkedHashMap<>();

	private Map<Integer, ColumnDataGeneratorSetting> columnIndexs = new LinkedHashMap<>();

	private CachedEvaluator evaluator;

	public void addColumn(ColumnDataGeneratorSetting col, int index) {
		columns.put(col.getName(), col);
		columnIndexs.put(index, col);
	}

	public void addQueryDefinition(QueryDefinitionDataGeneratorSetting obj, int index) {
		queryDefinitions.put(obj.getGenerationGroup().toUpperCase(), obj);
	}

	/**
	 * 値のチェックを行います。
	 */
	public void check() {
		columns.entrySet().forEach(entry -> {
			String genGroup = entry.getValue().getGenerationGroup();
			if (!CommonUtils.isEmpty(genGroup)) {
				QueryDefinitionDataGeneratorSetting queryDef = queryDefinitions.get(genGroup);
				entry.getValue().setQueryDefinitionDataGeneratorSetting(queryDef);
			}
		});
	}

	/**
	 * 初期値を評価します
	 * 
	 * @param evaluator 式評価
	 */
	public synchronized void calculateInitialValues() {
		columns.entrySet().forEach(entry -> {
			final ColumnDataGeneratorSetting colSetting = entry.getValue();
			String expression = colSetting.getStartValue();
			if (!CommonUtils.isEmpty(expression)) {
				Object value = evaluator.getEvalExecutor(expression).eval(Collections.emptyMap());
				colSetting.setStartValueObject(value);
				startValues.put(colSetting.getName(), value);
			}
		});
		final Map<String, Object> map = CommonUtils.map();
		map.put("_start", startValues);
		columns.entrySet().forEach(entry -> {
			final ColumnDataGeneratorSetting colSetting = entry.getValue();
			String expression = colSetting.getMaxValue();
			if (!CommonUtils.isEmpty(expression)) {
				Object value = evaluator.getEvalExecutor(expression).eval(map);
				colSetting.setMaxValueObject(value);
				maxValues.put(colSetting.getName(), value);
			}
		});
	}

	/**
	 * 開始値参照時のキー
	 */
	public static final String START_KEY = "_start";
	/**
	 * 最大値参照時のキー
	 */
	public static final String MAX_KEY = "_max";
	/**
	 * インデックス参照時のキー
	 */
	public static final String INDEX_KEY = "_index";
	/**
	 * 前の値参照時のキー
	 */
	public static final String PREVIOUS_KEY = "_previous";

	private ParametersContext startValues = new ParametersContext();
	private ParametersContext maxValues = new ParametersContext();

	private Map<String, Object> previousValues = Collections.emptyMap();

	/**
	 * 値を生成します
	 * 
	 * @param index     生成順番
	 * @param evaluator 式評価
	 * @return 生成した値
	 */
	public Map<String, Object> generateValue(long index) {
		final Map<String, Object> map = CommonUtils.map();
		map.put("_index", index);
		map.put("_previous", previousValues);
		map.put("_start", startValues);
		map.put("_max", maxValues);
		final int intIndex = (int) (index % Integer.MAX_VALUE);
		for (Map.Entry<String, ColumnDataGeneratorSetting> entry : columns.entrySet()) {
			final ColumnDataGeneratorSetting colSetting = entry.getValue();
			// クエリグループから取得
			if (colSetting.getQueryDefinitionDataGeneratorSetting() != null) {
				final Map<String, Object> queryValueMap = colSetting.getQueryDefinitionDataGeneratorSetting()
						.getValueMap(intIndex);
				map.put(colSetting.getName(), queryValueMap.get(colSetting.getName()));
				continue;
			}
			// Valuesから取得
			final Optional<Object> op = colSetting.getValue(intIndex);
			if (op.isPresent()) {
				map.put(colSetting.getName(), op.get());
				continue;
			}
			if (index == 0 || CommonUtils.isEmpty(colSetting.getNextValue())) {
				map.put(colSetting.getName(), colSetting.getStartValueObject());
			} else {
				// Next Valueから取得
				String expression = colSetting.getNextValue();
				Object value = evaluator.getEvalExecutor(expression).eval(map);
				if (colSetting.getMaxValueObject() != null) {
					int comp = compare(colSetting.getMaxValueObject(), value);
					if (comp > 0) {
						map.put(colSetting.getName(), value);
					} else {
						map.put(colSetting.getName(), colSetting.getStartValueObject());
					}
				} else {
					map.put(colSetting.getName(), value);
				}
			}
		}
		previousValues = map;
		return map;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public int compare(Object o1, Object o2) {
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
	public void loadData(Connection conn) throws SQLException {
		for (Map.Entry<String, QueryDefinitionDataGeneratorSetting> entry : queryDefinitions.entrySet()) {
			entry.getValue().loadData(conn);
		}
	}
}
