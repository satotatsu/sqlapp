/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.data.db.dialect.alloydb.sql.AlloyDBCreateIndexFactory;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Preserves ScaNN metadata with one set-based supplemental catalog query. */
public class AlloyDBIndexReader extends PostgresIndexReader {
	public AlloyDBIndexReader(Dialect dialect) { super(dialect); }

	@Override
	protected List<Index> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		var indexes = super.doGetAll(connection, context, version);
		Map<List<String>, Index> candidates = new HashMap<>();
		for (var index : indexes) {
			if (index.getIndexType() == null || index.getIndexType() == IndexType.Function
					|| index.getIndexType() == IndexType.Vector)
				candidates.put(List.of(index.getSchemaName(), index.getTableName(), index.getName()), index);
		}
		if (candidates.isEmpty()) return indexes;
		execute(connection, getSqlNodeCache().getString("scannIndexes.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rows) throws SQLException {
				var index = candidates.get(List.of(rows.getString("schema_name"), rows.getString("table_name"),
						rows.getString("index_name")));
				if (index == null) return;
				index.setIndexType(IndexType.Vector);
				index.setVectorDistanceType(switch (rows.getString("distance")) {
				case "cosine" -> VectorDistanceType.Cosine;
				case "l2" -> VectorDistanceType.Euclidean;
				case "dot_product" -> VectorDistanceType.DotProduct;
				default -> null;
				});
				index.setTableSpaceName(rows.getString("tablespace"));
				index.getSpecifics().put(AlloyDBCreateIndexFactory.METHOD, "scann");
				index.getSpecifics().put(AlloyDBCreateIndexFactory.KEYS, rows.getString("keys"));
				String options = rows.getString("options");
				if (options != null) index.getSpecifics().put(AlloyDBCreateIndexFactory.OPTIONS, options);
			}
		});
		return indexes;
	}
}
