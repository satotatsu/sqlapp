/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.jdbc.sql;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.util.ToStringBuilder;

/**
 * 生成されたキーをハンドルするインタフェース
 * 
 * @author satoh
 *
 */
public interface GeneratedKeyHandler {

	/**
	 * 行番号、生成対象のカラム名と生成された値が渡されます。
	 * 
	 * @param rowNo
	 *            行番号
	 * @param generatedKeyInfo
	 *            生成されたキー情報
	 */
	void handle(long rowNo, GeneratedKeyInfo generatedKeyInfo);

	/**
	 * 生成されたキー情報
	 * 
	 * @author tatsuo satoh
	 *
	 */
	public static class GeneratedKeyInfo implements Serializable {
		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 885479078079064921L;
		/** カタログ名 */
		private final Object catalogName;
		/** スキーマ名 */
		private final Object schemaName;
		/** テーブル名 */
		private final Object tableName;
		/** カラム名 */
		private final Object columnName;
		/** カラムラベル名 */
		private final Object columnLabel;
		/** 生成されたキー値 */
		private final Object value;
		/** 生成されたカラムの順番(0～) */
		private final int columnNo;

		private final Converters converters;

		protected GeneratedKeyInfo(ResultSetMetaData metaData, ResultSet rs,
				int columnNo) throws SQLException {
			this.catalogName = metaData.getCatalogName(columnNo);
			this.schemaName = metaData.getSchemaName(columnNo);
			this.tableName = metaData.getTableName(columnNo);
			this.columnName = metaData.getColumnName(columnNo);
			this.columnLabel = metaData.getColumnLabel(columnNo);
			this.value = rs.getObject(columnNo);
			this.columnNo = columnNo - 1;
			this.converters = Converters.getDefault();
		}

		/**
		 * @return the catalogName
		 */
		public Object getCatalogName() {
			return catalogName;
		}

		/**
		 * @return the schemaName
		 */
		public Object getSchemaName() {
			return schemaName;
		}

		/**
		 * @return the tableName
		 */
		public Object getTableName() {
			return tableName;
		}

		/**
		 * @return the columnName
		 */
		public Object getColumnName() {
			return columnName;
		}

		/**
		 * @return the columnLabel
		 */
		public Object getColumnLabel() {
			return columnLabel;
		}

		/**
		 * @return the value
		 */
		public Object getValue() {
			return value;
		}

		/**
		 * @return the value
		 */
		public <T> T getValue(Class<T> clazz) {
			return converters.convertObject(value, clazz);
		}

		/**
		 * @return the columnNo
		 */
		public int getColumnNo() {
			return columnNo;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			ToStringBuilder builder = new ToStringBuilder();
			builder.add("catalogName", catalogName);
			builder.add("schemaName", schemaName);
			builder.add("tableName", tableName);
			builder.add("columnName", columnName);
			builder.add("columnLabel", columnLabel);
			builder.add("value", value);
			return builder.toString();
		}

	}

}
