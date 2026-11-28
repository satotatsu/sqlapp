/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.TableRelationTreeHolder;
import com.sqlapp.data.schemas.TableRelationTreeHolder.TableRelation;
import com.sqlapp.exceptions.MissingWhereClauseKeyException;
import com.sqlapp.jdbc.sql.SqlParser;

class ConditionBasedSqlFactoryTest extends AbstractStandardFactoryTest {

	@Test
	void keylessTableSupportsConditionBasedUpdatesAndDeletes() {
		Table table = table("items");
		for (SqlType type : List.of(SqlType.UPDATE_TABLE, SqlType.UPDATE_FOR_APP, SqlType.DELETE_FOR_APP)) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			factory.getTableOptions().setUpdateAllCondition((t, builder) ->
					builder.lineBreak().and().name("id").eq().space()._add(7));
			String sql = factory.createSql(table).get(0).getSqlText();
			assertTrue(sql.contains("WHERE 1=1"), sql);
			if (type == SqlType.UPDATE_TABLE) {
				assertTrue(sql.contains("AND \"id\" = 7"), sql);
			} else {
				assertTrue(sql.contains("/*if isNotEmpty(id) */"), sql);
			}
			if (type != SqlType.DELETE_FOR_APP) {
				assertTrue(sql.contains("\"value\" ="), sql);
			}
		}
	}

	@Test
	void ordinaryRowOperationsStillRequireAKey() {
		Table table = table("items");
		for (SqlType type : List.of(SqlType.UPDATE, SqlType.DELETE)) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			assertThrows(MissingWhereClauseKeyException.class, () -> factory.createSql(table));
		}
	}

	@Test
	void availableKeysRemainExcludedFromConditionBasedUpdateAssignments() {
		Table table = table("items");
		table.setPrimaryKey(table.getColumns().get("id"));
		for (SqlType type : List.of(SqlType.UPDATE_TABLE, SqlType.UPDATE_FOR_APP)) {
			SqlFactory<Table> factory = sqlFactoryRegistry.getSqlFactory(table, type);
			String assignments = factory.createSql(table).get(0).getSqlText().split("WHERE")[0];
			assertFalse(assignments.contains("\"id\" ="), assignments);
			assertTrue(assignments.contains("\"value\" ="), assignments);
		}
	}

	@Test
	void rootRowDeleteExecutesAcrossTwoThreeAndFourLevels() throws Exception {
		for (int depth : List.of(2, 3, 4)) {
			List<Table> tables = new ArrayList<>();
			for (int i = 0; i < depth; i++) {
				Table current = table("t" + i);
				current.setPrimaryKey(current.getColumns().get("id"));
				if (i > 0) {
					current.getConstraints().addForeignKeyConstraint("fk" + i,
							current.getColumns().get("value"), tables.get(i - 1).getColumns().get("id"));
				}
				tables.add(current);
			}
			TableRelation relation = new TableRelationTreeHolder(tables).getTableRelation(tables.getLast());
			SqlFactory<TableRelation> factory = sqlFactoryRegistry.getSqlFactory(relation, SqlType.DELETE_BY_ROOT_ROWS);
			var node = SqlParser.getInstance().parse(dialect, factory.createSql(relation).get(0));
			var rootRow = tables.getFirst().getRows().newElement();
			rootRow.put("id", 1);
			try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:" + UUID.randomUUID(), "SA", "");
					var statement = connection.createStatement()) {
				for (Table table : tables) {
					statement.execute("CREATE TABLE \"" + table.getName() + "\" (\"id\" INT, \"value\" INT)");
					statement.execute("INSERT INTO \"" + table.getName() + "\" VALUES (1,1),(2,2)");
				}
				try (var parameters = node.eval(relation, List.of(rootRow));
						var delete = connection.prepareStatement(parameters.getSql())) {
					assertEquals(1, parameters.getParameterSize());
					delete.setInt(1, 1);
					assertEquals(1, delete.executeUpdate(), "depth=" + depth);
				}
				try (var remaining = statement.executeQuery("SELECT \"id\" FROM \"" + tables.getLast().getName() + "\"")) {
					assertTrue(remaining.next());
					assertEquals(2, remaining.getInt(1));
					assertFalse(remaining.next());
				}
				try (var parameters = node.eval(relation, List.of());
						var delete = connection.prepareStatement(parameters.getSql())) {
					assertEquals(0, delete.executeUpdate());
				}
			}
		}
	}

	private Table table(String name) {
		Table table = new Table(name);
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		table.getColumns().add(new Column("value").setDataType(DataType.INT));
		return table;
	}
}
