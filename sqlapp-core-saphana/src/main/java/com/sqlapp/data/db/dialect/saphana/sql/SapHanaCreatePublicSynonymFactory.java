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

package com.sqlapp.data.db.dialect.saphana.sql;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePublicSynonymFactory;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.util.CommonUtils;

/**
 * {@code CREATE PUBLIC SYNONYM <name> FOR [<database>.]<schema>.<object>}
 */
public class SapHanaCreatePublicSynonymFactory extends AbstractCreatePublicSynonymFactory<SapHanaSqlBuilder> {

	@Override
	protected void addCreateObject(final PublicSynonym synonym, final SapHanaSqlBuilder builder) {
		if (CommonUtils.isBlank(synonym.getObjectName())) {
			throw new IllegalArgumentException(
					"SAP HANA public synonym requires a target object name: " + synonym.getName());
		}
		final boolean remote = !CommonUtils.isBlank(synonym.getDbLinkName());
		if (remote && CommonUtils.isBlank(synonym.getObjectSchemaName())) {
			throw new IllegalArgumentException(
					"SAP HANA remote public synonym requires a target schema: " + synonym.getName());
		}
		builder.create().public_().synonym().space().name(synonym).space()._add("FOR").space();
		if (remote) {
			builder.name(synonym.getDbLinkName())._add(".");
		}
		if (!CommonUtils.isBlank(synonym.getObjectSchemaName())) {
			builder.name(synonym.getObjectSchemaName())._add(".");
		}
		builder.name(synonym.getObjectName());
	}
}
