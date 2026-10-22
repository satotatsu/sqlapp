/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

/** Visible database properties; this reader does not execute SET commands. */
public class HsqlSettingReader extends SettingReader {
	protected HsqlSettingReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Setting> doGetAll(Connection connection, ParametersContext context, ProductVersionInfo version) {
		if (version.getMajorVersion() < 2)
			return List.of();
		var result = new ArrayList<Setting>();
		execute(connection, getSqlNodeCache().getString("settings2.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(ExResultSet rs) throws SQLException {
				var setting = new Setting(getString(rs, "PROPERTY_NAME")).setValue(getString(rs, "PROPERTY_VALUE"));
				for (String field : List.of("PROPERTY_SCOPE", "PROPERTY_NAMESPACE", "PROPERTY_CLASS"))
					setSpecifics(rs, field, setting);
				result.add(setting);
			}
		});
		return result;
	}
}
