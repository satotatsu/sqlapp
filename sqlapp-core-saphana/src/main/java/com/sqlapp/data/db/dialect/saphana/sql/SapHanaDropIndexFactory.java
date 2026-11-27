/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.List;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.saphana.SapHanaCloud;
import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

/** Platform fulltext indexes have their own DROP statement. */
public class SapHanaDropIndexFactory extends SimpleSqlFactory<Index, SapHanaSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(final Index index) {
		var builder = createSqlBuilder()
				._add(index.getIndexType() == IndexType.FullText && !(getDialect() instanceof SapHanaCloud)
						? "DROP FULLTEXT INDEX"
						: "DROP INDEX")
				.name(index, getOptions().isDecorateSchemaName());
		List<SqlOperation> result = new ArrayList<>();
		addSql(result, builder, SqlType.DROP, index);
		return result;
	}
}
