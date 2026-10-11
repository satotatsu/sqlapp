/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-sqlserver.
 *
 * sqlapp-core-sqlserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-sqlserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-sqlserver.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateSequenceFactory;
import com.sqlapp.data.schemas.Sequence;

public class SapHanaCreateSequenceFactory extends AbstractCreateSequenceFactory<SapHanaSqlBuilder> {
	@Override
	protected void addCache(final Sequence obj, SapHanaSqlBuilder builder) {
		if (obj.getCacheSize() < 0) throw new IllegalArgumentException("SAP HANA sequence cache size must not be negative");
		if (!obj.isCache() || obj.getCacheSize() <= 1) {
			builder.no().cache();
		} else {
			super.addCache(obj, builder);
		}
	}

	@Override
	protected void addOptions(final Sequence obj, SapHanaSqlBuilder builder) {
		if (!obj.isCache() && obj.getCacheSize() == null) {
			builder.no().cache();
		}
		final String resetQuery = obj.getSpecifics().get("RESET_BY_QUERY");
		if (resetQuery != null && !resetQuery.isBlank()) {
			builder.space()._add("RESET BY ")._add(resetQuery);
		}
	}

}
