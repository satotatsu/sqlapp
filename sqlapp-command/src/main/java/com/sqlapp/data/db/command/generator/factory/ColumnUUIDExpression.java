package com.sqlapp.data.db.command.generator.factory;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public class ColumnUUIDExpression implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -5062000939737947451L;

	@Override
	public String apply(Column column) {
		if (column.getDataType() == DataType.UUID) {
			return "java.util.UUID.randomUUID()";
		}
		return null;
	}

}
