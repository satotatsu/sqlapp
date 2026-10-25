/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

class MySqlSchemaDefaultsTest {
	@org.junit.jupiter.api.Test
	void singleDefaultAndServerDefaultCreationRemainAvailable() {
		var registry = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0).createSqlFactoryRegistry();
		Schema original = new Schema("app");
		SqlFactory<Schema> create = registry.getSqlFactory(original, SqlType.CREATE);
		assertEquals("CREATE SCHEMA IF NOT EXISTS app", create.createSql(original).get(0).getSqlText());
		SqlFactory<Schema> alter = registry.getSqlFactory(original, SqlType.ALTER);
		alter.getOptions().setSetSearchPathToSchema(false);
		Schema target = original.clone();
		target.setCharacterSet("latin1");
		assertEquals(List.of("ALTER SCHEMA app DEFAULT CHARACTER SET latin1"),
				texts(alter.createDiffSql(original.diff(target))));
		assertEquals("CREATE SCHEMA IF NOT EXISTS app DEFAULT CHARACTER SET latin1",
				create.createSql(target).get(0).getSqlText());
		target.setCharacterSet(null);
		target.setCollation("latin1_bin");
		assertEquals(List.of("ALTER SCHEMA app DEFAULT COLLATE latin1_bin"),
				texts(alter.createDiffSql(original.diff(target))));
		assertEquals("CREATE SCHEMA IF NOT EXISTS app DEFAULT COLLATE latin1_bin",
				create.createSql(target).get(0).getSqlText());
	}

	@ParameterizedTest
	@CsvSource({ "MySql,5,5", "MySql,5,7", "MySql,8,0", "MySql,8,4" })
	void createsAndAltersBothDefaultsWithoutLosingUnchangedOptions(String product, int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect(product, major, minor, 0).createSqlFactoryRegistry();
		Schema original = new Schema("app data");
		original.setCharacterSet("utf8mb4");
		original.setCollation("utf8mb4_general_ci");
		SqlFactory<Schema> create = registry.getSqlFactory(original, SqlType.CREATE);
		assertEquals(
				"CREATE SCHEMA IF NOT EXISTS `app data` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci",
				create.createSql(original).get(0).getSqlText());
		SqlFactory<Schema> alter = registry.getSqlFactory(original, SqlType.ALTER);
		alter.getOptions().setSetSearchPathToSchema(false);
		Schema target = original.clone();
		target.setCollation("utf8mb4_bin");
		assertEquals(List.of("ALTER SCHEMA `app data` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_bin"),
				texts(alter.createDiffSql(original.diff(target))));
		target.setCharacterSet("latin1");
		target.setCollation("latin1_bin");
		assertEquals(List.of("ALTER SCHEMA `app data` DEFAULT CHARACTER SET latin1 DEFAULT COLLATE latin1_bin"),
				texts(alter.createDiffSql(original.diff(target))));
		assertTrue(alter.createDiffSql(original.diff(original.clone())).isEmpty());
		target = original.clone();
		target.setRemarks("documentation only");
		assertTrue(alter.createDiffSql(original.diff(target)).isEmpty());
		target = original.clone();
		var table = new com.sqlapp.data.schemas.Table("new_table");
		table.getColumns()
				.add(new com.sqlapp.data.schemas.Column("id").setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		target.getTables().add(table);
		var childSql = texts(alter.createDiffSql(original.diff(target)));
		assertFalse(childSql.isEmpty());
		assertTrue(childSql.stream().anyMatch(sql -> sql.contains("CREATE TABLE")), childSql.toString());
		assertTrue(childSql.stream().noneMatch(String::isBlank));
	}

	@ParameterizedTest
	@CsvSource({ "MySql,5,7", "MySql,8,4" })
	void invalidOptionsAndUnsupportedRemovalOrRenameFailClearly(String product, int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect(product, major, minor, 0).createSqlFactoryRegistry();
		Schema original = new Schema("app");
		original.setCharacterSet("utf8mb4");
		original.setCollation("utf8mb4_bin");
		SqlFactory<Schema> alter = registry.getSqlFactory(original, SqlType.ALTER);
		Schema target = original.clone();
		target.setCharacterSet(null);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
		target.setCharacterSet("utf8mb4");
		target.setCollation(null);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
		target.setCollation("utf8mb4_bin");
		target.setName("renamed");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
		target.setName("app");
		target.setCollation("utf8mb4_bin; DROP DATABASE app");
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(original.diff(target)));
		SqlFactory<Schema> create = registry.getSqlFactory(target, SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> create.createSql(target));
	}

	private List<String> texts(List<SqlOperation> operations) {
		return operations.stream().map(SqlOperation::getSqlText).toList();
	}
}
