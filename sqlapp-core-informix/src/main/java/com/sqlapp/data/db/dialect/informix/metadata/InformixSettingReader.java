/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

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

/**
 * Effective server configuration, with distinct boot-time and default values.
 */
public class InformixSettingReader extends SettingReader {
	public InformixSettingReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Setting> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo version) {
		final List<Setting> result = new ArrayList<>();
		execute(connection, getSqlNodeCache().getString("settings.sql"), context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final String name = rs.getString("setting_name");
				if (name == null || name.isBlank())
					throw new IllegalStateException("Missing Informix setting name");
				final var setting = new Setting(name.stripTrailing());
				setting.setId(getString(rs, "setting_id"));
				setting.setValue(value(rs, "effective_value"));
				setting.setDefaultValue(value(rs, "default_value"));
				setting.setDefault(setting.getValue() != null && setting.getDefaultValue() != null
						&& setting.getValue().equals(setting.getDefaultValue()));
				final String boot = value(rs, "boot_value");
				if (boot != null)
					setting.getSpecifics().put("INFORMIX_BOOT_VALUE", boot);
				result.add(setting);
			}
		});
		return result;
	}

	private static String value(final ExResultSet rs, final String label) throws SQLException {
		final String value = rs.getString(label);
		return value == null ? null : value.stripTrailing();
	}
}
