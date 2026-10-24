/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class Db2CreateMviewFactory extends Db2CreateTableFactory {
	@Override
	public List<SqlOperation> createSql(Table mview) {
		if (CommonUtils.isEmpty(mview.getDefinition())) {
			throw new IllegalArgumentException(
					"Db2 MQT requires its complete CREATE TABLE definition: " + mview.getName());
		}
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : com.sqlapp.data.db.dialect.db2.util.Db2CreationContext
				.sql(mview.getSpecifics().get("QUALIFIER"), mview.getSpecifics().get("FUNC_PATH"))) {
			addSql(result, createSqlBuilder()._add(sql), SqlType.SET_SEARCH_PATH_TO_SCHEMA, mview);
		}
		var builder = createSqlBuilder();
		builder._add(mview.getDefinition());
		addSql(result, builder, SqlType.CREATE, mview);
		addIndexDefinitions(mview, result);
		addOtherDefinitions(mview, result);
		return result;
	}
}
