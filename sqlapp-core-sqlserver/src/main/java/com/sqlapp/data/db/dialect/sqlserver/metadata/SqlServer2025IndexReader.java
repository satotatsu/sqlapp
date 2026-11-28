/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import java.sql.SQLException;
import java.util.Locale;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.node.SqlNode;

final class SqlServer2025IndexReader extends SqlServer2022IndexReader {
	SqlServer2025IndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected SqlNode getSqlSqlNode(ProductVersionInfo version) {
		return getSqlNodeCache().getString("indexes2025.sql");
	}

	@Override
	protected Index createIndex(ExResultSet rs) throws SQLException {
		Index index = super.createIndex(rs);
		if (rs.getInt("type") == 8) {
			index.setIndexType(IndexType.Vector);
			String metric = getString(rs, "distance_metric");
			if (metric == null || !"DiskANN".equalsIgnoreCase(getString(rs, "vector_index_type")))
				throw new SQLException("Unsupported or missing VECTOR index metadata for " + index.getName());
			index.setVectorDistanceType(switch (metric.toUpperCase(Locale.ROOT)) {
			case "COSINE" -> VectorDistanceType.Cosine;
			case "EUCLIDEAN" -> VectorDistanceType.Euclidean;
			case "DOT" -> VectorDistanceType.DotProduct;
			default -> throw new SQLException("Unsupported VECTOR index distance metric: " + metric);
			});
		} else if (index.getIndexType() == IndexType.Json) {
			String paths = getString(rs, "json_index_paths");
			if (paths == null)
				throw new SQLException("Missing JSON paths for index " + index.getName());
			if (!"[\"$\"]".equals(paths))
				index.getSpecifics().put("json_index_paths", paths);
			if (rs.getBoolean("optimize_for_array_search"))
				index.getSpecifics().put("optimize_for_array_search", "true");
		}
		return index;
	}
}
