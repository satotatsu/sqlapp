/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mysql.
 *
 * sqlapp-core-mysql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-mysql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-mysql.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.mysql.util;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.AbstractColumn;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.NamedArgument;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.ReferenceColumnCollection;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.sql.ParameterDirection;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * MySQL用のSQLビルダー
 * 
 * @author tatsuo satoh
 * 
 */
public class MySqlSqlBuilder extends AbstractSqlBuilder<MySqlSqlBuilder> {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	public MySqlSqlBuilder(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected MySqlSqlBuilder autoIncrement(AbstractColumn<?> column) {
		space()._add("AUTO_INCREMENT");
		return instance();
	}

	@Override
	protected MySqlSqlBuilder comment(AbstractColumn<?> column) {
		if (!CommonUtils.isEmpty(column.getRemarks())) {
			comment()._add(
					" '" + CommonUtils.left(column.getRemarks().replace("'", "''"), 255) + "'");
		}
		return instance();
	}

	@Override
	protected MySqlSqlBuilder checkConstraintDefinition(
			Column column) {
		return instance();
	}

	/**
	 * カラムのCharacter Setの定義を追加します
	 * 
	 * @param column
	 *            カラム
	 */
	@Override
	protected MySqlSqlBuilder characterSetDefinition(Column column) {
		if (!isCharcterSetType(column)) {
			return instance();
		}
		Table table = column.getAncestor(Table.class);
		if (table != null) {
			if (!CommonUtils.eq(table.getCharacterSet(),
					column.getCharacterSet())
					&& !CommonUtils.isEmpty(column.getCharacterSet())) {
				characterSet().space()._add(column.getCharacterSet());
			}
		} else {
			if (CommonUtils.isEmpty(column.getCharacterSet())) {
				characterSet().space()._add(column.getCharacterSet());
			}
		}
		return instance();
	}

	private boolean isCharcterSetType(Column column) {
		if (column.getDataType().isCharacter()) {
			return true;
		}
		if (column.getDataType() == DataType.SET
				|| column.getDataType() == DataType.ENUM) {
			return true;
		}
		return true;
	}

	/**
	 * カラムのCollateの定義を追加します
	 * 
	 * @param column
	 *            カラム
	 */
	@Override
	protected MySqlSqlBuilder collateDefinition(Column column) {
		if (!isCharcterSetType(column)) {
			return instance();
		}
		Table table = column.getAncestor(Table.class);
		if (table != null) {
			if (!CommonUtils.eq(table.getCollation(), column.getCollation())
					&& !CommonUtils.isEmpty(column.getCollation())) {
				collate().space()._add(column.getCollation());
			}
		} else {
			if (CommonUtils.isEmpty(column.getCollation())) {
				collate().space()._add(column.getCollation());
			}
		}
		return instance();
	}

	/**
	 * trueを追加します
	 * 
	 */
	public MySqlSqlBuilder _true() {
		appendElement("true");
		return instance();
	}

	/**
	 * LOCAL句を追加します
	 * 
	 */
	public MySqlSqlBuilder local() {
		appendElement("LOCAL");
		return instance();
	}

	/**
	 * USE句を追加します
	 * 
	 */
	public MySqlSqlBuilder use() {
		appendElement("USE");
		return instance();
	}

	/**
	 * AFTER句を追加します
	 * 
	 */
	public MySqlSqlBuilder after() {
		appendElement("AFTER");
		return instance();
	}

	/**
	 * EVENT句を追加します
	 * 
	 */
	public MySqlSqlBuilder event() {
		appendElement("EVENT");
		return instance();
	}

	/**
	 * SCHEDULE句を追加します
	 * 
	 */
	public MySqlSqlBuilder schedule() {
		appendElement("SCHEDULE");
		return instance();
	}

	/**
	 * AT句を追加します
	 * 
	 */
	public MySqlSqlBuilder at() {
		appendElement("AT");
		return instance();
	}

	public MySqlSqlBuilder every() {
		appendElement("EVERY");
		return instance();
	}

	public MySqlSqlBuilder starts() {
		appendElement("STARTS");
		return instance();
	}

	public MySqlSqlBuilder ends() {
		appendElement("ENDS");
		return instance();
	}

	public MySqlSqlBuilder completion() {
		appendElement("COMPLETION");
		return instance();
	}

	public MySqlSqlBuilder enable() {
		appendElement("ENABLE");
		return instance();
	}

	public MySqlSqlBuilder disable() {
		appendElement("DISABLE");
		return instance();
	}

	public MySqlSqlBuilder rowFormat() {
		appendElement("ROW_FORMAT");
		return instance();
	}

	/**
	 * DUPLICATE句を追加します
	 * 
	 */
	public MySqlSqlBuilder duplicate() {
		appendElement("DUPLICATE");
		return instance();
	}

	@Override
	public MySqlSqlBuilder argument(NamedArgument argument) {
		if (argument.getDirection() != ParameterDirection.Input) {
			this._add(argument.getDirection());
			this._add(" ");
		}
		if (argument.getName() != null) {
			this._add(argument.getName());
			this._add(" ");
		}
		this.typeDefinition(argument);
		return instance();
	}

	/**
	 * カラムのデフォルト型定義を追加します
	 * 
	 * @param column
	 */
	@Override
	protected MySqlSqlBuilder defaultDefinition(Column column) {
		if (column.getDefaultValue() == null) {
			return instance();
		}
		if (column.getDataType() == DataType.DATETIME
				|| column.getDataType() == DataType.TIME) {
			if (CommonUtils.isEmpty(column.getDefaultValue())) {
				return instance();
			}
			if (!column.getDefaultValue().startsWith("'")) {
				return instance();
			}
		}
		return super.defaultDefinition(column);
	}
	
	@Override
	public MySqlSqlBuilder clone(){
		return (MySqlSqlBuilder)super.clone();
	}

	/**
	 * カラム名称を追加します
	 * 
	 * @param columns
	 */
	public MySqlSqlBuilder names(ReferenceColumnCollection columns) {
		for (int i = 0; i < columns.size(); i++) {
			ReferenceColumn column = columns.get(i);
			comma(i > 0).appendQuoteColumnName(column.getName());
			if (column.getLength()!=null) {
				this._add("(");
				this._add(column.getLength());
				this._add(")");
			}
			order(column.getOrder());
		}
		return instance();
	}

}
