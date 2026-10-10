/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.Postgres110TableReader;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.parameter.ParametersContext;

public class Yugabyte11TableReader extends Postgres110TableReader {
	public Yugabyte11TableReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void setMetadataDetail(Connection connection, ParametersContext context, List<Table> tables)
			throws SQLException {
		super.setMetadataDetail(connection, context, tables);
		for (Table table : tables)
			YugabytePlacementReader.read(connection, table);
	}
}
