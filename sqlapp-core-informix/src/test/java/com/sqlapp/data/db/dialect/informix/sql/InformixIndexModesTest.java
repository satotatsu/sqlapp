/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.dialect.informix.util.InformixIndexModes;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class InformixIndexModesTest {
	@Test
	void preservesModesThroughXmlAndCreatesThemDirectly() throws Exception {
		for (String state : java.util.List.of("E", "D", "F", "G")) {
			final Table table = new Table("items");
			table.getColumns().add("id", c -> c.setDataType(DataType.INT));
			final Index index = new Index("ix").setUnique(true);
			index.getColumns().add(table.getColumns().get(0));
			InformixIndexModes.apply(index, state);
			table.getIndexes().add(index);
			final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
			final Index copy = restored.getIndexes().get(0);
			assertEquals(!state.equals("D"), copy.isEnable());
			final var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(copy,
					SqlType.CREATE);
			assertEquals(1, operations.size());
			final String sql = operations.get(0).getSqlText();
			if (!state.equals("E"))
				assertTrue(sql.endsWith(InformixIndexModes.sql(copy)), sql);
			assertFalse(sql.contains("SET INDEXES"));
		}
	}

	@Test
	void rejectsUnknownNonUniqueAndContradictoryFilteringModes() {
		final Index index = new Index("ix");
		assertThrows(IllegalStateException.class, () -> InformixIndexModes.apply(index, "?"));
		assertThrows(IllegalStateException.class, () -> InformixIndexModes.apply(index, "F"));
		index.setUnique(true);
		InformixIndexModes.apply(index, "G");
		index.setEnable(false);
		assertThrows(IllegalArgumentException.class, () -> InformixIndexModes.sql(index));
		index.setEnable(true).setUnique(false);
		assertThrows(IllegalArgumentException.class, () -> InformixIndexModes.sql(index));
		index.setUnique(true);
		index.getSpecifics().put(InformixIndexModes.MODE, "invalid");
		assertThrows(IllegalArgumentException.class, () -> InformixIndexModes.sql(index));
		InformixIndexModes.apply(index, null);
		assertNull(InformixIndexModes.sql(index));
		assertFalse(index.getSpecifics().containsKey(InformixIndexModes.MODE));
	}
}
