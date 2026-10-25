/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.mariadb.sql;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.mysql.sql.MySqlCreateIndexFactory;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;

class MariadbPhysicalOptionsTest {
	@ParameterizedTest
	@CsvSource({ "10,2", "10,11", "11,4" })
	void inheritedTableAndFulltextOptionsRemainNative(int major, int minor) {
		var registry = DialectResolver.getInstance().getDialect("MariaDB", major, minor, 0).createSqlFactoryRegistry();
		Table original = new Table("t");
		original.getColumns().add("code", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		original.getSpecifics().put("ENGINE", "MyISAM");
		original.getSpecifics().put("ROW_FORMAT", "Dynamic");
		original.setRemarks("old comment");
		SqlFactory<Table> create = registry.getSqlFactory(original, SqlType.CREATE);
		assertTrue(create.createSql(original).get(0).getSqlText().contains("ROW_FORMAT =DYNAMIC"));
		SqlFactory<Table> alter = registry.getSqlFactory(original, SqlType.ALTER);
		Table target = original.clone();
		target.setRemarks(null);
		assertEquals("ALTER TABLE t COMMENT ''", alter.createDiffSql(original.diff(target)).get(0).getSqlText());
		Index index = new Index("ft_code");
		index.setIndexType(IndexType.FullText);
		index.getColumns().add(target.getColumns().get("code"));
		index.getSpecifics().put(MySqlCreateIndexFactory.PARSER, "my_parser");
		target.getIndexes().add(index);
		assertTrue(create.createSql(target).get(0).getSqlText().contains("WITH PARSER `my_parser`"));
		assertTrue(alter.createDiffSql(original.diff(target)).get(0).getSqlText().contains("WITH PARSER `my_parser`"));
		target.getSpecifics().put("ENGINE", "bad; engine");
		assertThrows(IllegalArgumentException.class, () -> create.createSql(target));
		target.getSpecifics().put("ENGINE", "MyISAM");
		target.setCompression(true);
		assertThrows(IllegalArgumentException.class, () -> create.createSql(target));
	}
}
