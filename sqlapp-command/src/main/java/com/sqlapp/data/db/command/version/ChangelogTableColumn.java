package com.sqlapp.data.db.command.version;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;

/**
 * Changelog Table Columns
 */
public enum ChangelogTableColumn {
	/** ID PK */
	CHANGE_NUMBER() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.BIGINT);
		}
	},
	/** APPLIED_BY */
	APPLIED_BY() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.NVARCHAR).setLength(255);
		}
	},
	/** APPLIED_AT */
	APPLIED_AT() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.DATETIME);
		}
	},
	/** STATUS */
	STATUS() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.NVARCHAR).setLength(31);
		}
	},
	/** DESCRIPTION */
	DESCRIPTION() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.NVARCHAR).setLength(1023);
		}
	},
	/** SERIES_NUMBER */
	SERIES_NUMBER() {
		@Override
		public Column createColumn(String name) {
			return new Column(name).setDataType(DataType.BIGINT);
		}
	},;

	/**
	 * CREATE COLUMN
	 * 
	 * @param name column name
	 * @return Column
	 */
	public Column createColumn(String name) {
		return null;
	}

}
