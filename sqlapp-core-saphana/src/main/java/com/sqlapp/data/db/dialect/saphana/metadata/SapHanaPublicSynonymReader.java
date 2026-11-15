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

import static com.sqlapp.util.CommonUtils.list;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.metadata.PublicSynonymReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.PublicSynonym;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

/**
 * Reads SAP HANA public synonyms ({@code SYNONYMS.SCHEMA_NAME = 'PUBLIC'}).
 * <p>
 * When no schema filter is given, public synonyms that point at the SYS and
 * _SYS_* catalog schemas are excluded because SAP HANA creates thousands of
 * them for its own catalog views. With a schema filter, the filter applies to
 * the synonym target schema.
 */
public class SapHanaPublicSynonymReader extends PublicSynonymReader {

	protected SapHanaPublicSynonymReader(final Dialect dialect) {
		super(dialect);
	}

	@Override
	protected List<PublicSynonym> doGetAll(final Connection connection, final ParametersContext context,
			final ProductVersionInfo productVersionInfo) {
		final boolean multiDatabase = isMultiDatabase(productVersionInfo);
		final SqlNode node = getSqlNodeCache().getString(multiDatabase ? "publicSynonyms2.sql" : "publicSynonyms.sql");
		final List<PublicSynonym> result = list();
		execute(connection, node, context, new ResultSetNextHandler() {
			@Override
			public void handleResultSetNext(final ExResultSet rs) throws SQLException {
				result.add(createSynonym(rs, multiDatabase));
			}
		});
		return result;
	}

	/** SAP HANA 2.0 exposes OBJECT_DATABASE for cross-database synonym targets. */
	protected boolean isMultiDatabase(final ProductVersionInfo productVersionInfo) {
		return productVersionInfo.getMajorVersion() != null && productVersionInfo.getMajorVersion() >= 2;
	}

	protected PublicSynonym createSynonym(final ExResultSet rs, final boolean multiDatabase) throws SQLException {
		final PublicSynonym synonym = new PublicSynonym(getString(rs, SYNONYM_NAME));
		synonym.setId("" + rs.getLong("SYNONYM_OID"));
		synonym.setObjectSchemaName(getString(rs, OBJECT_SCHEMA));
		synonym.setObjectName(getString(rs, OBJECT_NAME));
		if (multiDatabase) {
			synonym.setDbLinkName(getString(rs, "TARGET_DATABASE"));
		}
		return synonym;
	}
}
