/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-hsql.
 *
 * sqlapp-core-hsql is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-hsql is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-hsql.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTemporaryTableFactory;
import com.sqlapp.data.schemas.Table;

/**
 * CREATE TEMPORARY TABLE
 * 
 * @author satoh
 * 
 */
public class VirticaCreateTemporaryTableFactory extends AbstractCreateTemporaryTableFactory<VirticaSqlBuilder> {

	@Override
	protected void addCreateObject(final Table obj, final VirticaSqlBuilder builder) {
		String external = obj.getSpecifics().get("TABLE_DEFINITION", String.class);
		if (external != null && !external.isBlank())
			throw new IllegalArgumentException(
					"Vertica external COPY definitions cannot be converted to local temporary tables: "
							+ obj.getName());
		if (obj.getTableType() == Table.TableType.Flex
				|| Boolean.TRUE.equals(obj.getSpecifics().get("IS_FLEXTABLE", Boolean.class)))
			throw new IllegalArgumentException(
					"Vertica temporary Flex table recreation requires vendor DDL: " + obj.getName());
		String prefix = this.getTableOptions().getTemporaryTableNamePrefix().apply(obj);
		String suffix = this.getTableOptions().getTemporaryTableNameSuffix().apply(obj);
		builder.create().local().temporary().table().ifNotExists(this.getOptions().isCreateIfNotExists());
		builder.space()._add(
				this.getDialect().getTemporaryTableName(obj, prefix, suffix, this.getOptions().isDecorateSchemaName()));
	}

	@Override
	protected void addOption(final Table table, final VirticaSqlBuilder builder) {
		String action = table.getSpecifics().get("COMMIT_ACTION", String.class);
		if (action == null) {
			super.addOption(table, builder);
			return;
		}
		String normalized = action.trim().toUpperCase(java.util.Locale.ROOT);
		if (!normalized.equals("DELETE") && !normalized.equals("PRESERVE"))
			throw new IllegalArgumentException("Vertica COMMIT_ACTION must be DELETE or PRESERVE: " + action);
		builder.on().commit().space()._add(normalized).rows();
	}
}
