/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-core-saphana.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Table;

public class SapHanaCreateTableFactory extends AbstractCreateTableFactory<SapHanaSqlBuilder> {

	@Override
	protected void addCreateObject(final Table obj, final SapHanaSqlBuilder builder) {
		builder.create();
		builder.column(obj.getTableDataStoreType()!=null||obj.getTableDataStoreType().isColumn());
		builder.row(obj.getTableDataStoreType()!=null&&obj.getTableDataStoreType().isRow());
		builder.table();
		builder.name(obj, this.getOptions().isDecorateSchemaName());
	}
}
