/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-saphana.
 *
 * sqlapp-core-saphana is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-saphana is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-saphana.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.SettingReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.Setting;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads SAP HANA configuration parameters as {@link Setting}s.
 * <p>
 * A setting is named {@code <ini file>/<section>/<key>}, for example
 * {@code global.ini/persistence/log_mode}. The effective value comes from
 * {@code M_CONFIGURATION_PARAMETER_VALUES}, and its description, data type,
 * unit and default value from {@code CONFIGURATION_PARAMETER_PROPERTIES}. When
 * {@code M_CONFIGURATION_PARAMETER_VALUES} is not available, the effective
 * value is resolved from {@code M_INIFILE_CONTENTS} by layer priority
 * (DATABASE, HOST, SYSTEM, DEFAULT).
 * <p>
 * Users without a privilege for the monitoring views (for example CATALOG READ)
 * see no parameters, so the result is empty rather than an error. When a
 * scale-out system reports a parameter for several services with different
 * values, the first value is kept and {@code VALUE_VARIES_BY_SERVICE} is set.
 */
public class SapHanaSettingReader extends SettingReader {

	static final String VALUE_VARIES_BY_SERVICE = "VALUE_VARIES_BY_SERVICE";

	protected SapHanaSettingReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<Setting> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean parameterViews = hasConfigurationParameterViews(connection);
		final SqlNode node = getSqlNodeCache().getString(parameterViews ? "settings.sql" : "settingsInifile.sql");
		final Map<String, Setting> result = new LinkedHashMap<>();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				final Setting setting = createSetting(rs, parameterViews);
				final Setting first = result.putIfAbsent(setting.getName(), setting);
				if (first != null && !Objects.equals(first.getValue(), setting.getValue())) {
					first.getSpecifics().put(VALUE_VARIES_BY_SERVICE, "true");
				}
			}
		});
		return new ArrayList<>(result.values());
	}

	protected boolean hasConfigurationParameterViews(final Connection connection) {
		final boolean[] result = new boolean[1];
		execute(connection, getSqlNodeCache().getString("configurationParameterViews.sql"), new ParametersContext(),
				new ResultSetNextHandler() {
					@Override
					public void handleResultSetNext(final ExResultSet rs) throws SQLException {
						result[0] = rs.getInt("VIEW_COUNT") == 2;
					}
				});
		return result[0];
	}

	protected Setting createSetting(final ExResultSet rs, final boolean parameterViews) throws SQLException {
		final Setting setting = new Setting(getString(rs, "SETTING_NAME"));
		setting.setValue(getString(rs, "VALUE"));
		setting.setDisplayValue(getString(rs, "RAW_VALUE"));
		setting.setDefaultValue(getString(rs, "DEFAULT_VALUE"));
		setting.setDefault("DEFAULT".equalsIgnoreCase(getString(rs, "LAYER_NAME")));
		setSpecifics(rs, "FILE_NAME", setting);
		setSpecifics(rs, "SECTION", setting);
		setSpecifics(rs, "KEY", setting);
		setSpecifics(rs, "LAYER_NAME", setting);
		if (parameterViews) {
			setting.setRemarks(getString(rs, "DESCRIPTION"));
			setSpecifics(rs, "DATA_TYPE_NAME", setting);
			setSpecifics(rs, "UNIT", setting);
			setSpecifics(rs, "RESTART_REQUIRED", setting);
		}
		return setting;
	}
}
