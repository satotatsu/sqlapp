/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static com.sqlapp.util.CommonUtils.trim;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Domain;
import com.sqlapp.jdbc.ExResultSet;

/** Firebird 3.0 introduced domain ownership in RDB$FIELDS. */
public class Firebird30DomainReader extends FirebirdDomainReader {
	protected Firebird30DomainReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected Domain createDomain(ExResultSet rs) throws SQLException {
		Domain domain = super.createDomain(rs);
		String owner = trim(getString(rs, "RDB$OWNER_NAME"));
		if (owner != null) {
			domain.getSpecifics().put("firebird.owner", owner);
		}
		return domain;
	}
}
