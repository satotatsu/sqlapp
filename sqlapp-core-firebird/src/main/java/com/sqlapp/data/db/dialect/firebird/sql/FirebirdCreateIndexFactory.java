/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/** Firebird indexes have a single direction for all keys. */
public class FirebirdCreateIndexFactory extends AbstractCreateIndexFactory<FirebirdSqlBuilder> {
	@Override
	protected boolean createIndex(Index index) {
		com.sqlapp.data.db.dialect.firebird.util.FirebirdPrivilege.identifier(index.getName(), "index name");
		if (index.getTable() == null)
			throw new IllegalArgumentException("Firebird index requires a table");
		for (var constraint : index.getTable().getConstraints())
			if (index.getName().equals(constraint.getName())
					|| index.getName().equals(constraint.getSpecifics().get("firebird.indexName")))
				return false;
		return true;
	}

	@Override
	public void addObjectDetail(Index obj, Table table, FirebirdSqlBuilder builder) {
		if (table == null || obj.getColumns().isEmpty()) {
			throw new IllegalArgumentException("Firebird index requires a table and at least one key");
		}
		if (obj.getIndexType() != null && obj.getIndexType() != IndexType.BTree
				&& obj.getIndexType() != IndexType.Function)
			throw new IllegalArgumentException("Firebird supports only BTree and computed indexes");
		if (obj.isCompression() || obj.getCompressionType() != null || obj.getPartitioning() != null
				|| obj.getTableSpaceName() != null || obj.getVectorDistanceType() != null)
			throw new IllegalArgumentException(
					"Firebird indexes do not support compression, partitioning, tablespaces or vector distance options");
		boolean descending = obj.getColumns().get(0).getOrder() == Order.Desc;
		for (var column : obj.getColumns()) {
			if ((column.getOrder() == Order.Desc) != descending || column.getNullsOrder() != null) {
				throw new IllegalArgumentException(
						"Firebird index keys require a single direction and no NULLS ordering");
			}
		}
		boolean computed = obj.getIndexType() == IndexType.Function;
		if (computed && (obj.getColumns().size() != 1 || getDialect().compareTo(DialectHolder.defaultDialect20) < 0)) {
			throw new IllegalArgumentException(
					"Firebird computed index requires one expression and Firebird 2.0 or later");
		}
		if (!CommonUtils.isEmpty(obj.getWhere()) && getDialect().compareTo(DialectHolder.defaultDialect50) < 0) {
			throw new IllegalArgumentException("Firebird partial indexes require Firebird 5.0 or later");
		}
		builder.unique(obj.isUnique()).space()._add(descending ? "DESCENDING" : "ASCENDING").index();
		builder.space().name(obj, false).on().name(table, false).space();
		if (computed) {
			// Catalog expressions include their parentheses; keep the source intact.
			String expression = obj.getColumns().get(0).getName();
			builder._add("COMPUTED BY (")._add(expression)._add(")");
		} else {
			builder._add("(");
			for (int i = 0; i < obj.getColumns().size(); i++) {
				builder.comma(i > 0).name(obj.getColumns().get(i));
			}
			builder._add(")");
		}
		if (!CommonUtils.isEmpty(obj.getWhere())) {
			builder.lineBreak().where().space()._add(obj.getWhere());
		}
	}

	@Override
	public List<SqlOperation> createSql(Index obj) {
		List<SqlOperation> result = super.createSql(obj);
		if (!result.isEmpty() && obj.getRemarks() != null)
			result.addAll(getSqlFactoryRegistry().createSql(obj, SqlType.SET_COMMENT));
		if (!result.isEmpty() && !obj.isEnable()) {
			var builder = createSqlBuilder();
			builder.alter().index().space().name(obj, false).inactive();
			addSql(result, builder, SqlType.ALTER, obj);
		}
		return result;
	}
}
