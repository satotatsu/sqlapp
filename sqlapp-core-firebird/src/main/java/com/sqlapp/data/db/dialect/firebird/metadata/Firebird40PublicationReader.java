/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.firebird.Firebird40;
import com.sqlapp.data.db.dialect.firebird.FirebirdPublication;

/**
 * One joined read for the publication and all memberships, available from
 * Firebird 4.0.
 */
public final class Firebird40PublicationReader {
	public Firebird40PublicationReader(Dialect dialect) {
		if (!(dialect instanceof Firebird40))
			throw new UnsupportedOperationException("Publication catalogs require Firebird 4.0 or later");
	}

	public FirebirdPublication read(Connection connection) throws SQLException {
		try (var statement = connection.prepareStatement("""
				SELECT P.RDB$PUBLICATION_NAME, P.RDB$ACTIVE_FLAG, P.RDB$AUTO_ENABLE, T.RDB$TABLE_NAME
				FROM RDB$PUBLICATIONS P
				LEFT JOIN RDB$PUBLICATION_TABLES T ON T.RDB$PUBLICATION_NAME = P.RDB$PUBLICATION_NAME
				ORDER BY P.RDB$PUBLICATION_NAME, T.RDB$TABLE_NAME
				"""); var rows = statement.executeQuery()) {
			Boolean active = null, autoEnable = null;
			var tables = new ArrayList<String>();
			while (rows.next()) {
				String publication = rows.getString(1);
				if (!"RDB$DEFAULT".equals(publication == null ? null : publication.stripTrailing()))
					throw new SQLException("Unsupported Firebird publication: " + rows.getString(1));
				boolean nextActive = flag(rows, 2), nextAuto = flag(rows, 3);
				if (active != null && (active != nextActive || autoEnable != nextAuto))
					throw new SQLException("Inconsistent Firebird publication catalog state");
				active = nextActive;
				autoEnable = nextAuto;
				String name = rows.getString(4);
				if (name != null)
					tables.add(name.stripTrailing());
			}
			if (active == null)
				throw new SQLException("Missing Firebird RDB$DEFAULT publication");
			return new FirebirdPublication(active, autoEnable, tables);
		}
	}

	private boolean flag(ResultSet rows, int index) throws SQLException {
		int value = rows.getInt(index);
		if (rows.wasNull() || value < 0 || value > 1)
			throw new SQLException("Invalid Firebird publication flag at column " + index);
		return value == 1;
	}
}
