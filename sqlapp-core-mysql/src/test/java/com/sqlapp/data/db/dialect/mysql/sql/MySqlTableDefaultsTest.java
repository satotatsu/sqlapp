/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

class MySqlTableDefaultsTest {
	@ParameterizedTest
	@CsvSource({ "5,1", "5,7", "8,0", "8,4", "9,0" })
	void retainsTableDefaultsAndDoesNotConvertUnchangedColumns(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		Table original = new Table("app data");
		original.setCharacterSet("utf8mb4");
		original.setCollation("utf8mb4_general_ci");
		original.getColumns().add("text", c -> c.setDataType(DataType.VARCHAR).setLength(30).setCharacterSet("latin1")
				.setCollation("latin1_bin"));
		SqlFactory<Table> create = registry.getSqlFactory(original, SqlType.CREATE);
		String sql = create.createSql(original).get(0).getSqlText();
		assertTrue(sql.contains("DEFAULT CHARACTER SET =utf8mb4 COLLATE =utf8mb4_general_ci"), sql);
		assertTrue(sql.contains("CHARACTER SET latin1 COLLATE latin1_bin"), sql);
		SqlFactory<Table> alter = registry.getSqlFactory(original, SqlType.ALTER);
		Table target = original.clone();
		target.setCollation("utf8mb4_bin");
		sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertEquals("ALTER TABLE `app data` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", sql);
		assertFalse(sql.contains("CONVERT"));
		target.getColumns().get("text").setLength(50);
		sql = alter.createDiffSql(original.diff(target)).get(0).getSqlText();
		assertTrue(sql.contains("MODIFY"), sql);
		assertTrue(sql.contains(", DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin"), sql);
		assertTrue(sql.contains("CHARACTER SET latin1 COLLATE latin1_bin"), sql);
		assertFalse(sql.contains("CONVERT"));
	}

	@ParameterizedTest
	@CsvSource({ "5,7", "8,4" })
	void handlesSingleDefaultsAndRejectsRemovalAndInvalidTokens(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MySql", major, minor, 0).createSqlFactoryRegistry();
		Table original = new Table("t");
		original.getColumns().add("id", c -> c.setDataType(DataType.INT));
		SqlFactory<Table> alter = registry.getSqlFactory(original, SqlType.ALTER);
		SqlFactory<Table> create = registry.getSqlFactory(original, SqlType.CREATE);
		Table target = original.clone();
		target.setCollation("utf8mb4_bin");
		assertEquals("ALTER TABLE t COLLATE utf8mb4_bin",
				alter.createDiffSql(original.diff(target)).get(0).getSqlText());
		target.setCollation(null);
		target.setCharacterSet("utf8mb4");
		assertEquals("ALTER TABLE t DEFAULT CHARACTER SET utf8mb4",
				alter.createDiffSql(original.diff(target)).get(0).getSqlText());
		assertTrue(create.createSql(target).get(0).getSqlText().contains("DEFAULT CHARACTER SET =utf8mb4"));
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(target.diff(original)));
		target.setCharacterSet("utf8mb4; DROP TABLE t");
		assertThrows(IllegalArgumentException.class, () -> create.createSql(target));
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
	}
}
