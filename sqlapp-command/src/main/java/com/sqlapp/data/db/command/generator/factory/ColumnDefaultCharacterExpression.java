package com.sqlapp.data.db.command.generator.factory;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public class ColumnDefaultCharacterExpression implements ColumnFunction<String> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -5062000939737947451L;

	@Override
	public String apply(Column column) {
		if (column.getLength() == null) {
			return "nextAlphaNumeric(10)";
		}
		return "nextAlphaNumeric( " + column.getLength() + " )";
	}

}
