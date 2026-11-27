/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.jdbc.ExResultSet;

class OracleColumnDomainReferenceTest {
	@Test
	void metadataRetainsOwnerComponentAndAssociationWithoutAdditionalQuery() throws Exception {
		Map<String, String> values = Map.of("OWNER", "APP", "TABLE_NAME", "T", "COLUMN_NAME", "C", "DATA_TYPE",
				"NUMBER", "NULLABLE", "Y", "DOMAIN_OWNER", "Other Schema", "DOMAIN_NAME", "Address",
				"DOMAIN_COLUMN_NAME", "Zip", "DOMAIN_ASSOCIATION_ID", "12");
		Column column = new Oracle23aiColumnReader(DialectHolder.oracle23aiDialect).createColumn(new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name);
			}

			@Override
			public Object getObject(String name) {
				return null;
			}

			@Override
			public long getLong(String name) {
				return 0;
			}

			@Override
			public int getInt(String name) {
				return 0;
			}

			@Override
			public boolean wasNull() {
				return true;
			}

			@Override
			public java.sql.Timestamp getTimestamp(String name) {
				return null;
			}
		});
		assertEquals("Address", column.getDomainName());
		assertEquals("Other Schema", column.getDomainSchemaName());
		assertEquals("Zip", column.getDomainColumnName());
		assertEquals("12", column.getSpecifics().get("oracle.domain.associationId"));
	}
}
