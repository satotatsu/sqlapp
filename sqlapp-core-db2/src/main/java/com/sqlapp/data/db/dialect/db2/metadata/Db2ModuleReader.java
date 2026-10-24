/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.db2.Db2_1110;
import com.sqlapp.data.db.metadata.PackageReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Package;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/**
 * SQL modules and PL/SQL packages; aliases belong to the existing
 * SynonymReader.
 */
public class Db2ModuleReader extends PackageReader {
	public Db2ModuleReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Package> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (!(getDialect() instanceof Db2_1110))
			throw new IllegalArgumentException("Db2 module reader requires LUW 11.1 or later");
		List<Package> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("modules.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				Package module = new Package(getString(rs, "MODULENAME"));
				module.setSchemaName(getString(rs, "MODULESCHEMA"));
				module.setDialect(getDialect());
				module.setRemarks(rs.getString("REMARKS"));
				module.setCreatedAt(rs.getTimestamp("CREATE_TIME"));
				module.getSpecifics().put("DB2_OBJECT_TYPE", "MODULE");
				for (String key : List.of("MODULETYPE", "DIALECT", "OWNER", "BASE_MODULESCHEMA", "BASE_MODULENAME",
						"MEMBER_COUNT")) {
					String value = rs.getString(key);
					if (value != null)
						module.getSpecifics().put(key, value);
				}
				result.add(module);
			}
		});
		return result;
	}
}
