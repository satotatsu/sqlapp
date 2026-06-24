/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command.dataconfig;

import java.io.File;

import com.sqlapp.data.schemas.rowiterator.DataFormat;
import com.sqlapp.util.CommonUtils;

public enum ConfigFileType {
	EXCEL() {
		@Override
		public DataFormat getWorkbookFileType() {
			return DataFormat.EXCEL;
		}
	},
	JSON() {
		@Override
		public DataFormat getWorkbookFileType() {
			return DataFormat.JSON;
		}
	},
	TOML() {
		@Override
		public DataFormat getWorkbookFileType() {
			return DataFormat.TOML;
		}
	},
	YAML() {
		@Override
		public DataFormat getWorkbookFileType() {
			return DataFormat.YAML;
		}
	};

	public DataFormat getWorkbookFileType() {
		return null;
	}

	public static ConfigFileType parse(File file) {
		if (CommonUtils.isEmpty(file)) {
			return null;
		}
		for (ConfigFileType enm : ConfigFileType.values()) {
			if (enm.getWorkbookFileType().match(file.getName())) {
				return enm;
			}
		}
		return null;
	}

	public static ConfigFileType parse(String value) {
		if (CommonUtils.isEmpty(value)) {
			return null;
		}
		DataFormat workbookFileType = DataFormat.parse(value);
		if (workbookFileType == null) {
			return null;
		}
		for (ConfigFileType enm : ConfigFileType.values()) {
			if (enm.getWorkbookFileType() == workbookFileType) {
				return enm;
			}
		}
		return null;
	}

}
