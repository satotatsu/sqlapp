package com.sqlapp.data.db.command.generator.factory;

import com.sqlapp.data.db.command.generator.setting.TableDataGeneratorSetting;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public class ColumnNextValue implements ColumnFunction<String> {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	private ColumnDefaultCharacterExpression charExpression = new ColumnDefaultCharacterExpression();
	private ColumnUUIDExpression uuidExpression = new ColumnUUIDExpression();

	@Override
	public String apply(Column column) {
		if (column.getDataType() == DataType.BOOLEAN) {
			return "!" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName();
		}
		if (column.getDataType() == DataType.DOUBLE) {
			return "nextDouble(0.0d, 1000.0d)";
		}
		if (column.getDataType() == DataType.FLOAT) {
			return "nextDouble(0.0f, 1000.0f)";
		}
		if (column.getDataType().isNumeric()) {
			return "" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + " + 1";
		}
		if (column.getDataType() == DataType.TIMESTAMP || column.getDataType() == DataType.DATETIME) {
			return "addMilliSeconds(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.TIME) {
			return "addSeconds(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType() == DataType.DATE) {
			return "addDays(" + TableDataGeneratorSetting.PREVIOUS_KEY + "." + column.getName() + ",1)";
		}
		if (column.getDataType().isCharacter()) {
			return charExpression.apply(column);
		}
		if (column.getDataType() == DataType.UUID) {
			return uuidExpression.apply(column);
		}
		return null;
	}

}
