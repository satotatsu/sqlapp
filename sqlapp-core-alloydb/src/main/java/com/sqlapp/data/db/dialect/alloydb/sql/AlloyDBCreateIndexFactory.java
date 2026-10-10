/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.alloydb.sql;

import com.sqlapp.data.db.dialect.postgres.sql.Postgres150CreateIndexFactory;
import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** ScaNN DDL from catalog-deparsed key expressions and SQL options. */
public class AlloyDBCreateIndexFactory extends Postgres150CreateIndexFactory {
	public static final String METHOD = "alloydb.index.method";
	public static final String KEYS = "alloydb.scann.keys";
	public static final String OPTIONS = "alloydb.scann.options";

	@Override
	public void addObjectDetail(Index index, Table table, PostgresSqlBuilder builder) {
		String method = index.getSpecifics().get(METHOD);
		if (method == null && index.getIndexType() != com.sqlapp.data.schemas.IndexType.Vector) {
			super.addObjectDetail(index, table, builder);
			return;
		}
		if (method != null && !"scann".equals(method)) throw new IllegalArgumentException("Unsupported AlloyDB index method: " + method);
		String keys = index.getSpecifics().get(KEYS);
		if (keys == null) {
			if (index.getColumns().size() != 1 || index.getVectorDistanceType() == null)
				throw new IllegalArgumentException("ScaNN requires one column and vectorDistanceType, or " + KEYS);
			String distance = switch (index.getVectorDistanceType()) {
			case Cosine -> "cosine";
			case Euclidean -> "l2";
			case InnerProduct, DotProduct -> "dot_product";
			default -> throw new IllegalArgumentException("Unsupported ScaNN vectorDistanceType: " + index.getVectorDistanceType());
			};
			keys = getDialect().quote(index.getColumns().get(0).getName()) + " " + distance;
		}
		if (keys.isBlank()) throw new IllegalArgumentException("ScaNN requires non-empty " + KEYS);
		if (table == null || index.isUnique() || !index.getIncludes().isEmpty())
			throw new IllegalArgumentException("ScaNN requires a table and a non-unique index without INCLUDE columns");
		addUnique(index, table, builder);
		builder.name(index, false).on().name(table, getOptions().isDecorateSchemaName())
				.space()._add("USING scann (")._add(keys)._add(")");
		String options = index.getSpecifics().get(OPTIONS);
		if (options != null && !options.isBlank()) builder.space()._add("WITH (")._add(options)._add(")");
		if (index.getTableSpaceName() != null)
			builder.space()._add("TABLESPACE ").name(index.getTableSpaceName());
		addFilter(index, table, builder);
	}
}
