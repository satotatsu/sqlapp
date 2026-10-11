/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.yugabyte.metadata;

import java.sql.SQLException;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresTableSpaceReader;
import com.sqlapp.data.schemas.TableSpace;
import com.sqlapp.jdbc.ExResultSet;

public class YugabyteTableSpaceReader extends PostgresTableSpaceReader {
	public YugabyteTableSpaceReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected TableSpace createTableSpace(ExResultSet rs) throws SQLException {
		TableSpace result = super.createTableSpace(rs);
		var options = rs.getArray("spcoptions");
		if (options != null) {
			try {
				for (Object value : (Object[]) options.getArray()) {
					String option = value.toString();
					if (option.startsWith("replica_placement="))
						result.getSpecifics().put("YSQL_REPLICA_PLACEMENT",
								option.substring("replica_placement=".length()));
				}
			} finally {
				options.free();
			}
		}
		return result;
	}
}
