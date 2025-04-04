package com.sqlapp.jdbc.sql;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.util.TypeInformation;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.util.ToStringBuilder;

/**
 * 生成されたキーの情報を管理します
 */
public class GeneratedKeyInfo implements Serializable {
	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 885479078079064921L;
	/** カタログ名 */
	private final String catalogName;
	/** スキーマ名 */
	private final String schemaName;
	/** テーブル名 */
	private final String tableName;
	/** カラム名 */
	private final String columnName;
	/** カラムラベル名 */
	private final String columnLabel;
	/** カラム型 */
	private java.sql.JDBCType columnJdbcType;
	/** カラム型名 */
	private DataType columnDataType;
	/** カラム型名 */
	private final String columnTypeName;
	/** カラムクラス名 */
	private final String columnClassName;
	/** 生成されたキー値 */
	private final Object value;
	/** 生成されたカラムの順番(0～) */
	private final int columnNo;
	/** autoIncrement */
	private final boolean autoIncrement;

	private final Converters converters;

	protected GeneratedKeyInfo(ResultSetMetaData metaData, ResultSet rs, int columnNo) throws SQLException {
		this(metaData, rs, columnNo, null);
	}

	protected GeneratedKeyInfo(ResultSetMetaData metaData, ResultSet rs, int columnNo, Dialect dialect)
			throws SQLException {
		this.catalogName = metaData.getCatalogName(columnNo);
		this.schemaName = metaData.getSchemaName(columnNo);
		this.tableName = metaData.getTableName(columnNo);
		this.columnName = metaData.getColumnName(columnNo);
		this.columnLabel = metaData.getColumnLabel(columnNo);
		this.columnTypeName = metaData.getColumnTypeName(columnNo);
		this.autoIncrement = metaData.isAutoIncrement(columnNo);
		this.columnClassName = metaData.getColumnClassName(columnNo);
		if (dialect != null) {
			final Optional<TypeInformation> optional = dialect.getDbDataTypes()
					.matchTypeInformation(this.columnTypeName, null);
			if (optional.isPresent()) {
				if (optional.get().getDataType().isPresent()) {
					this.columnDataType = optional.get().getDataType().get();
					this.columnJdbcType = columnDataType.getJdbcType();
				} else {
					this.columnJdbcType = java.sql.JDBCType.valueOf(metaData.getColumnType(columnNo));
				}
			} else {
				this.columnJdbcType = java.sql.JDBCType.valueOf(metaData.getColumnType(columnNo));
			}
		} else {
			this.columnJdbcType = java.sql.JDBCType.valueOf(metaData.getColumnType(columnNo));
		}
		this.value = rs.getObject(columnNo);
		this.columnNo = columnNo - 1;
		this.converters = Converters.getDefault();
	}

	/**
	 * @return the catalogName
	 */
	public String getCatalogName() {
		return catalogName;
	}

	/**
	 * @return the schemaName
	 */
	public String getSchemaName() {
		return schemaName;
	}

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @return the columnName
	 */
	public String getColumnName() {
		return columnName;
	}

	/**
	 * @return the columnLabel
	 */
	public String getColumnLabel() {
		return columnLabel;
	}

	/**
	 * @return the value
	 */
	public Object getValue() {
		return value;
	}

	/**
	 * @return the autoIncrement
	 */
	public boolean isAutoIncrement() {
		return autoIncrement;
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

	/**
	 * @return the columnJdbcType
	 */
	public java.sql.JDBCType getColumnJdbcType() {
		return columnJdbcType;
	}

	/**
	 * @return the columnDataType
	 */
	public DataType getColumnDataType() {
		return columnDataType;
	}

	/**
	 * @return the columnClassName
	 */
	public String getColumnClassName() {
		return columnClassName;
	}

	/**
	 * @return the columnTypeName
	 */
	public String getColumnTypeName() {
		return columnTypeName;
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
		builder.add("columnJdbcType", columnJdbcType);
		builder.add("columnDataType", columnDataType);
		builder.add("columnTypeName", columnTypeName);
		builder.add("value", value);
		return builder.toString();
	}
}
