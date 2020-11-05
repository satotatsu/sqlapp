/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-h2.
 *
 * sqlapp-core-h2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-h2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-h2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTriggerFactory;
import com.sqlapp.data.schemas.Trigger;

public class H2CreateTriggerFactory extends
		AbstractCreateTriggerFactory<H2SqlBuilder> {

	@Override
	protected void addCreateObject(final Trigger obj, H2SqlBuilder builder) {
		builder.create().ifNotExists(this.getOptions().isCreateIfNotExists()).trigger();
		builder.name(obj);
		addCreateObjectDetail(obj, builder);
	}

	@Override
	protected void addCreateTriggerBody(final Trigger obj, H2SqlBuilder builder) {
		builder.forEach().row();
		builder.call().name(obj.getClassName());
	}

}
