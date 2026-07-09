package com.sqlapp.jdbc.sql;

import java.util.List;

import org.apache.poi.ss.formula.functions.Rows;

import com.sqlapp.data.schemas.Table;

public enum ParameterCountStrategy {
	COLUMNS {
	},
	ROWS {
	},;

	public int countParameters(Table table, List<Rows> rows) {
		return rows.size();
	}
}
