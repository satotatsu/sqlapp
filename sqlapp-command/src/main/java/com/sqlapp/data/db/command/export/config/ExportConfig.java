/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.data.db.command.export.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sqlapp.data.schemas.rowiterator.DataFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportConfig {
	/** format */
	@JsonProperty(index = 0)
	private DataFormat format;
	/** SQL */
	@JsonProperty(index = 1)
	private String query;
	/** file encoding */
	@JsonProperty(index = 2)
	private String encoding = "UTF-8";
	/** file header */
	@JsonProperty(index = 3)
	private boolean header = true;
}
