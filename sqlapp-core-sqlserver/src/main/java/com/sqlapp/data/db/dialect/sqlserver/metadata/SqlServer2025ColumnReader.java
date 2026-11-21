/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

final class SqlServer2025ColumnReader extends SqlServer2016ColumnReader {
	SqlServer2025ColumnReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("columns2025.sql");
	}

	@Override
	protected Column createColumn(ExResultSet rs) throws SQLException {
		Column column = super.createColumn(rs);
		if (column.getDataType() == DataType.JSON) {
			column.setLength(null);
			column.setScale(null);
		} else if (column.getDataType() == DataType.VECTOR) {
			Integer dimension = getInteger(rs, "vector_dimensions");
			String element = getString(rs, "vector_base_type_desc");
			if (dimension == null || dimension < 1 || element == null)
				throw new SQLException("Incomplete VECTOR metadata for " + column.getName());
			column.setLength(null).setScale(null).setVectorDimension(dimension);
			if ("float32".equalsIgnoreCase(element))
				column.setVectorElementDataType(DataType.REAL);
			else if ("float16".equalsIgnoreCase(element))
				column.getSpecifics().put("vector_base_type", "FLOAT16");
			else
				throw new SQLException("Unrecognized SQL Server VECTOR base type: " + element);
		}
		return column;
	}
}
