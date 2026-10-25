/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.Db2Nickname;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

class Db2NicknameSqlTest {
	private Table nickname() {
		var table = new Table("Nick.Name").setSchemaName("Local.Schema");
		table.getColumns().add("ID", c -> c.setDataType(DataType.INT));
		Db2Nickname.set(table, new Db2Nickname.Snapshot(1, "Server.Name", "Remote.Schema", "Remote.Table", "R", "N",
				Map.of(), Map.of()));
		return table;
	}

	private List<String> sql(Table table, SqlType type) {
		SqlFactory<Table> factory = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry().getSqlFactory(table, type);
		return factory.createSql(table).stream().map(o -> o.getSqlText()).toList();
	}

	@Test
	void nativeCreateRetainsColumnsXmlCommentsAndSkipsRemoteIndexSpecifications() throws Exception {
		var table = nickname();
		table.setRemarks("nickname's comment");
		table.getColumns().get("ID").setRemarks("column's comment");
		table.getIndexes().add("REMOTE_INDEX", table.getColumns().get("ID"));
		Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertEquals(Db2Nickname.get(table), Db2Nickname.get(restored));
		assertEquals(Db2Nickname.get(table), Db2Nickname.get(table.clone()));
		var sql = sql(restored, SqlType.CREATE);
		assertEquals(
				"CREATE NICKNAME \"Local.Schema\".\"Nick.Name\" FOR \"Server.Name\".\"Remote.Schema\".\"Remote.Table\"",
				sql.getFirst());
		assertTrue(sql.stream().anyMatch(s -> s.contains("ALTER COLUMN \"ID\" LOCAL TYPE INTEGER")), sql.toString());
		assertTrue(sql.stream().anyMatch(s -> s.contains("DISALLOW CACHING")));
		assertTrue(sql.stream().anyMatch(s -> s.contains("nickname''s comment")));
		assertTrue(sql.stream().anyMatch(s -> s.contains("column''s comment")));
		assertFalse(sql.stream().anyMatch(s -> s.contains("CREATE INDEX") || s.contains("CREATE TABLE")));
		assertEquals(List.of("DROP NICKNAME \"Local.Schema\".\"Nick.Name\""), sql(table, SqlType.DROP));
		assertTrue(sql(new Table("NORMAL"), SqlType.DROP).getFirst().startsWith("DROP TABLE"));
	}

	@Test
	void renamedColumnsAreMovedThroughUniqueTemporaryNamesBeforeTypeChanges() {
		var table = nickname();
		table.getColumns().clear();
		table.getColumns().add("B", c -> c.setDataType(DataType.INT));
		table.getColumns().add("A", c -> c.setDataType(DataType.VARCHAR).setLength(30));
		Db2Nickname.set(table, new Db2Nickname.Snapshot(1, "SERVER", null, "T", "R", "Y", Map.of(),
				Map.of("B", Map.of("REMOTE_COLUMN_NAME", "A"), "A", Map.of("REMOTE_COLUMN_NAME", "B"))));
		var sql = sql(table, SqlType.CREATE);
		assertTrue(sql.getFirst().endsWith("FOR \"SERVER\".\"T\""));
		assertTrue(sql.get(1).endsWith("ALTER COLUMN \"A\" LOCAL NAME \"SQLAPP_NICKNAME_0\""));
		assertTrue(sql.get(2).endsWith("ALTER COLUMN \"B\" LOCAL NAME \"SQLAPP_NICKNAME_1\""));
		assertTrue(sql.get(3).endsWith("LOCAL NAME \"B\""));
		assertTrue(sql.get(4).endsWith("LOCAL NAME \"A\""));
		assertTrue(sql.getLast().endsWith(" ALLOW CACHING"));
	}

	@Test
	void complexSnapshotsNeedCompleteDefinitionsAndChangedNicknamesCannotBecomeAlterTable() {
		var table = nickname();
		var metadata = Db2Nickname.get(table);
		Db2Nickname.set(table, new Db2Nickname.Snapshot(1, metadata.serverName(), null, null, "N", "N",
				Map.of("FILE", "data.txt"), Map.of()));
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
		table.setDefinition("--#SET TERMINATOR @\n"
				+ "CREATE NICKNAME \"Local.Schema\".\"Nick.Name\" (ID INTEGER) FOR SERVER F OPTIONS (FILE 'data.txt')@\n"
				+ "ALTER NICKNAME \"Local.Schema\".\"Nick.Name\" DISALLOW CACHING@");
		assertEquals(2, sql(table, SqlType.CREATE).size());
		assertTrue(sql(table, SqlType.CREATE).getLast().endsWith("DISALLOW CACHING"));
		var changed = table.clone().setRemarks("changed");
		SqlFactory<Table> alter = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry().getSqlFactory(table,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(table.diff(changed)));
		assertTrue(alter.createDiffSql(table.diff(table.clone())).isEmpty());
		table.getSpecifics().put(Db2Nickname.SPECIFICS_KEY, "{\"format\":2}");
		assertThrows(RuntimeException.class, () -> sql(table, SqlType.CREATE));
	}

	@Test
	void catalogCreateAndSchemaAlterComposeNicknamesAndGrants() {
		Catalog catalog = new Catalog();
		Schema schema = new Schema("Local.Schema");
		catalog.getSchemas().add(schema);
		Table table = nickname();
		schema.getTables().add(table);
		catalog.getObjectPrivileges().add(new ObjectPrivilege().setSchemaName(schema.getName())
				.setObjectName(table.getName()).setPrivilege("SELECT").setGranteeName("PUBLIC").setHierachy(false));
		var registry = DialectHolder.Db2_1215Dialect.createSqlFactoryRegistry();
		SqlFactory<Catalog> create = registry.getSqlFactory(catalog, SqlType.CREATE);
		var created = create.createSql(catalog).stream().map(o -> o.getSqlText()).toList();
		int nickname = -1, grant = -1;
		for (int i = 0; i < created.size(); i++) {
			if (created.get(i).startsWith("CREATE NICKNAME"))
				nickname = i;
			if (created.get(i).startsWith("GRANT SELECT"))
				grant = i;
		}
		assertTrue(nickname >= 0 && grant > nickname, created.toString());
		Schema empty = new Schema(schema.getName());
		SqlFactory<Schema> alter = registry.getSqlFactory(schema, SqlType.ALTER);
		assertTrue(alter.createDiffSql(empty.diff(schema)).stream()
				.anyMatch(o -> o.getSqlText().startsWith("CREATE NICKNAME")));
		assertTrue(alter.createDiffSql(schema.diff(empty)).stream()
				.anyMatch(o -> o.getSqlText().startsWith("DROP NICKNAME")));
		var changed = schema.clone();
		changed.getTables().get(table.getName()).getColumns().get("ID").setDataType(DataType.BIGINT);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(schema.diff(changed)));
	}

	@Test
	void malformedOptionsAndAmbiguousRemoteNamesAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> new Db2Nickname.Snapshot(1, "S", null, "T", "R", "N", Map.of("bad option", "x"), Map.of()));
		assertThrows(IllegalArgumentException.class, () -> new Db2Nickname.Snapshot(1, "S", null, "T", "R", "N",
				Map.of("NAME", "x", "name", "y"), Map.of()));
		var table = nickname();
		Db2Nickname.set(table, new Db2Nickname.Snapshot(1, "S", null, "T", "R", "N", Map.of(),
				Map.of("ID", Map.of("REMOTE_COLUMN_NAME", "mixed.Case"))));
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
		Db2Nickname.set(table, new Db2Nickname.Snapshot(1, "S", null, "T", "R", "N", Map.of(),
				Map.of("ID", Map.of("NUMERIC_STRING", "Y"))));
		assertThrows(IllegalArgumentException.class, () -> sql(table, SqlType.CREATE));
	}
}
