/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.Set;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.util.Db2SqlBuilder;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.SchemaProperties;

/** Comment changes do not need to replace their owning objects. */
final class Db2AlterCommentSql {
	private Db2AlterCommentSql() {
	}

	static boolean onlyRemarks(DbObjectDifference difference) {
		return Set.of(SchemaProperties.REMARKS.getLabel()).containsAll(difference.getChangedProperties().keySet());
	}

	static String comment(Dialect dialect, String target, String remarks) {
		Db2SqlBuilder builder = new Db2SqlBuilder(dialect);
		builder._add("COMMENT ON " + target + " IS ");
		if (remarks == null)
			builder._add("NULL");
		else
			builder.sqlChar(remarks);
		return builder.toString();
	}
}
