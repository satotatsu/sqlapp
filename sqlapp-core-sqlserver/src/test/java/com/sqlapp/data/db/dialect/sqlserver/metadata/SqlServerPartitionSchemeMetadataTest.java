/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.db.dialect.sqlserver.util.SqlServerPartitionOptions;
import com.sqlapp.data.schemas.PartitionScheme;
import com.sqlapp.jdbc.ExResultSet;

class SqlServerPartitionSchemeMetadataTest {
	@Test
	void firstUnassignedDestinationIsNextUsedWithoutLosingMappings() throws Exception {
		var reader = new SqlServer2005PartitionSchemeReader(DialectHolder.defaultDialect2005);
		PartitionScheme scheme = new PartitionScheme("PS");
		SqlServerPartitionOptions.clearNextUsed(scheme);
		for (int destination = 1; destination <= 4; destination++) {
			int id = destination;
			reader.addDestination(scheme, new ExResultSet(null) {
				@Override
				public String getString(String name) {
					return "FG" + id;
				}

				@Override
				public int getInt(String name) {
					return name.equals("destination_id") ? id : 3;
				}
			});
			assertEquals(id <= 3 ? "" : "FG4", SqlServerPartitionOptions.getNextUsed(scheme));
		}
		assertEquals(4, scheme.getTableSpaces().size());
	}
}
