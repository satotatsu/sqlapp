package com.sqlapp.data.db.command.generator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sqlapp.util.CommonUtils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * クエリー生成設定
 */
@Getter
@Setter
@EqualsAndHashCode
public class QueryDefinitionDataGeneratorSetting {
	/** シート列名 */
	private String colString;
	/** 生成タイプ */
	private String generationGroup;
	/** SELECT SQL */
	private String selectSql;

	private List<Map<String, Object>> values = CommonUtils.list();

	/**
	 * DBからデータを読み込みます
	 * 
	 * @param conn DBコネクション
	 * @throws SQLException
	 */
	public void loadData(Connection conn) throws SQLException {
		try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			if (!stmt.execute(selectSql)) {
				return;
			}
			try (ResultSet rs = stmt.getResultSet()) {
				Map<Integer, String> indexNamelMap = CommonUtils.map();
				final ResultSetMetaData resultSetMetaData = rs.getMetaData();
				int colCount = resultSetMetaData.getColumnCount();
				for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
					final String label = resultSetMetaData.getColumnLabel(i);
					indexNamelMap.put((i - 1), label.intern());
				}
				while (rs.next()) {
					Map<String, Object> map = CommonUtils.map();
					for (int i = 0; i < colCount; i++) {
						String name = indexNamelMap.get(i);
						Object value = rs.getObject(i);
						map.put(name, value);
					}
					values.add(map);
				}
			}
		}
	}

	/**
	 * 値をインデックスを指定して取得します。
	 * 
	 * @param i
	 * @return
	 */
	public Map<String, Object> getValueMap(int i) {
		if (values.isEmpty()) {
			return Collections.emptyMap();
		}
		int size = values.size();
		int pos = i % size;
		return values.get(pos);
	}
}
