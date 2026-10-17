/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-oracle.
 *
 * sqlapp-core-oracle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-oracle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-oracle.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreatePublicSynonymFactory;
import com.sqlapp.data.schemas.PublicSynonym;

public class OracleCreatePublicSynonymFactory extends AbstractCreatePublicSynonymFactory<OracleSqlBuilder> {
	@Override
	public java.util.List<com.sqlapp.data.db.sql.SqlOperation> createDiffSql(
			com.sqlapp.data.schemas.DbObjectDifference difference) {
		if (difference.getState() == com.sqlapp.data.schemas.State.Unchanged)
			return java.util.List.of();
		if (difference.getState() != com.sqlapp.data.schemas.State.Modified)
			return super.createDiffSql(difference);
		if (!OracleSynonymTarget.validateChange(difference, getDialect()))
			return java.util.List.of();
		PublicSynonym before = difference.getOriginal(PublicSynonym.class),
				after = difference.getTarget(PublicSynonym.class);
		OracleSqlBuilder builder = createSqlBuilder(getDialect());
		addCreateObject(after, builder);
		return java.util.List
				.of(createOperation(builder.toString(), com.sqlapp.data.db.sql.SqlType.ALTER, before, after));
	}

	@Override
	protected void addCreateObject(final PublicSynonym obj, OracleSqlBuilder builder) {
		builder.create().or().replace().public_().synonym();
		builder.name(obj);
		builder.space().for_();
		OracleSynonymTarget.add(obj.getObjectSchemaName(), obj.getObjectName(), obj.getDbLinkName(), builder);
	}
}
