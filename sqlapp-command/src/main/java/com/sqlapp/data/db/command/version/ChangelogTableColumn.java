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
