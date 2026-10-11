/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-sybase.
 */
package com.sqlapp.data.db.dialect.sybase.metadata;

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/** Reads configured and current ASE server configuration values. */
public class SybaseSettingReader extends SettingReader {
	protected SybaseSettingReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Setting> doGetAll(final Connection connection,
			final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		SqlNode node = getSqlNodeCache().getString("settings.sql");
		List<Setting> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				Setting setting = new Setting(getString(rs, "setting_name"));
				setting.setId(getString(rs, "setting_id"));
				setting.setValue(getString(rs, "configured_value"));
				setting.setDisplayValue(getString(rs, "current_value"));
				setSpecifics(rs, "status", setting);
				result.add(setting);
			}
		});
		return result;
	}
}
