/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mysql.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

class MySqlPhysicalOptionsTest {
	@ParameterizedTest
	@CsvSource({"5,1", "5,7", "8,4", "9,0"})
	void rowFormatAndEngineSurviveCreateAndAlter(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table original = table();
		original.getSpecifics().put("ENGINE", "InnoDB");
		original.getSpecifics().put("ROW_FORMAT", "Dynamic");
		String sql = create(dialect, original);
		assertTrue(sql.contains("ENGINE =InnoDB"), sql);
		assertTrue(sql.contains("ROW_FORMAT =DYNAMIC"), sql);
		Table target = original.clone();
		target.getSpecifics().put("ENGINE", "MyISAM");
		target.getSpecifics().put("ROW_FORMAT", "FIXED");
		sql = alter(dialect, original, target);
		assertTrue(sql.contains("ENGINE =MyISAM, ROW_FORMAT =FIXED"), sql);
		target.getSpecifics().remove("ENGINE");
		assertThrows(IllegalArgumentException.class, () -> alter(dialect, original, target));
		target.getSpecifics().put("ENGINE", "InnoDB");
		target.getSpecifics().remove("ROW_FORMAT");
		assertThrows(IllegalArgumentException.class, () -> alter(dialect, original, target));
		for (String key : new String[] {"ENGINE", "ROW_FORMAT"}) {
			target.getSpecifics().put(key, "invalid; DROP TABLE t");
			assertThrows(IllegalArgumentException.class, () -> create(dialect, target));
			assertThrows(IllegalArgumentException.class, () -> alter(dialect, original, target));
			target.getSpecifics().remove(key);
		}
	}

	@ParameterizedTest
	@CsvSource({"5,1", "5,7", "8,4", "9,0"})
	void compressionRequiresConsistentExplicitRemoval(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table original = table();
		Table compressed = original.clone();
		compressed.setCompression(true);
		assertTrue(create(dialect, compressed).contains("ROW_FORMAT =COMPRESSED"));
		assertEquals("ALTER TABLE t ROW_FORMAT =COMPRESSED", alter(dialect, original, compressed));
		assertThrows(IllegalArgumentException.class, () -> alter(dialect, compressed, original));
		Table target = original.clone();
		target.getSpecifics().put("ROW_FORMAT", "DYNAMIC");
		assertEquals("ALTER TABLE t ROW_FORMAT =DYNAMIC", alter(dialect, compressed, target));
		target.setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> create(dialect, target));
		assertThrows(IllegalArgumentException.class, () -> alter(dialect, original, target));
	}

	@ParameterizedTest
	@CsvSource({"5,1", "5,7", "8,4", "9,0"})
	void clearsCommentsAndComposesWithColumnChanges(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table original = table();
		original.setRemarks("owner's comment");
		Table target = original.clone();
		target.setRemarks(null);
		assertEquals("ALTER TABLE t COMMENT ''", alter(dialect, original, target));
		target.setRemarks("");
		assertEquals("ALTER TABLE t COMMENT ''", alter(dialect, original, target));
		target.getColumns().get("code").setLength(50);
		String sql = alter(dialect, original, target);
		assertTrue(sql.contains("COMMENT '', MODIFY"), sql);
		target.setRemarks("new owner's comment");
		assertTrue(alter(dialect, original, target).contains("COMMENT 'new owner''s comment'"));
	}

	@ParameterizedTest
	@CsvSource({"5,1", "5,7", "8,4", "9,0"})
	void fulltextParserComposesAcrossAllCreatePaths(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table original = table();
		original.getSpecifics().put("ENGINE", "MyISAM");
		Table target = original.clone();
		Index index = new Index("ft_code");
		index.setIndexType(IndexType.FullText);
		index.getColumns().add(target.getColumns().get("code"));
		index.getSpecifics().put(MySqlCreateIndexFactory.PARSER, "parser`name");
		target.getIndexes().add(index);
		SqlFactory<Index> factory = dialect.createSqlFactoryRegistry().getSqlFactory(index, SqlType.CREATE);
		assertTrue(factory.createSql(index).get(0).getSqlText().contains("WITH PARSER `parser``name`"));
		assertTrue(create(dialect, target).contains("WITH PARSER `parser``name`"));
		assertTrue(alter(dialect, original, target).contains("WITH PARSER `parser``name`"));
		Table changed = target.clone();
		changed.getIndexes().get("ft_code").getSpecifics().put(MySqlCreateIndexFactory.PARSER, "other_parser");
		String sql = alter(dialect, target, changed);
		assertTrue(sql.contains("DROP INDEX ft_code, ADD FULLTEXT INDEX ft_code"), sql);
		assertTrue(sql.contains("WITH PARSER `other_parser`"), sql);
		for (String invalid : new String[] {"", " ", "bad\nparser", "bad\u0000parser"}) {
			index.getSpecifics().put(MySqlCreateIndexFactory.PARSER, invalid);
			assertThrows(IllegalArgumentException.class, () -> create(dialect, target));
		}
		index.getSpecifics().put(MySqlCreateIndexFactory.PARSER, "parser");
		index.setIndexType(IndexType.BTree);
		assertThrows(IllegalArgumentException.class, () -> create(dialect, target));
	}

	@ParameterizedTest
	@CsvSource({"8,0", "8,4", "9,0"})
	void visibilityOnlyChangesDoNotRebuildIndexes(int major, int minor) {
		Dialect dialect = dialect(major, minor);
		Table original = table();
		Index index = new Index("ix_code");
		index.getColumns().add(original.getColumns().get("code"));
		original.getIndexes().add(index);
		Table target = original.clone();
		target.getIndexes().get("ix_code").setEnable(false);
		assertEquals("ALTER TABLE t ALTER INDEX ix_code INVISIBLE", alter(dialect, original, target));
		assertEquals("ALTER TABLE t ALTER INDEX ix_code VISIBLE", alter(dialect, target, original));
		target.getIndexes().get("ix_code").setRemarks("changed");
		String sql = alter(dialect, original, target);
		assertTrue(sql.contains("DROP INDEX ix_code, ADD INDEX ix_code"), sql);
		assertTrue(sql.endsWith("COMMENT 'changed' INVISIBLE"), sql);
	}

	private String create(Dialect dialect, Table table) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(table, SqlType.CREATE);
		return factory.createSql(table).get(0).getSqlText();
	}
	private String alter(Dialect dialect, Table original, Table target) {
		SqlFactory<Table> factory = dialect.createSqlFactoryRegistry().getSqlFactory(target, SqlType.ALTER);
		return factory.createDiffSql(original.diff(target)).get(0).getSqlText();
	}
	private Table table() {
		Table table = new Table("t");
		table.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		return table;
	}
	private Dialect dialect(int major, int minor) {
		return DialectResolver.getInstance().getDialect("MySql", major, minor, 0);
	}
}
