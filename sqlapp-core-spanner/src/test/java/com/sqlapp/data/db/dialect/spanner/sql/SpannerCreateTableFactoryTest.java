/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;

class SpannerCreateTableFactoryTest extends SpannerSqlFactoryTest {

	@Test
	void testPrimaryKeyOutsideColumnList() {
		final Table table = new Table("SINGERS");
		table.setDialect(dialect);
		final Column shardId = new Column("SHARD_ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		final Column singerId = new Column("SINGER_ID")
				.setDataType(DataType.UUID).setNotNull(true);
		table.getColumns().add(shardId);
		table.getColumns().add(singerId);
		table.getConstraints().addPrimaryKeyConstraint("PK_SINGERS",
				shardId, singerId).getColumns().get("SINGER_ID")
				.setOrder(Order.Desc);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS SINGERS"), sql);
		assertTrue(sql.contains("SHARD_ID INT64"), sql);
		assertTrue(sql.contains("SINGER_ID UUID"), sql);
		assertTrue(sql.contains(
				") PRIMARY KEY ( SHARD_ID, SINGER_ID DESC )"), sql);
	}

	@Test
	void testUniqueConstraintAsUniqueIndex() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column id = new Column("ID")
				.setDataType(DataType.BIGINT).setNotNull(true);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320)
				.setNotNull(true);
		table.getColumns().add(id);
		table.getColumns().add(email);
		table.getConstraints().addPrimaryKeyConstraint("PK_USERS", id);
		table.getConstraints().addUniqueConstraint("UK_USERS_EMAIL", email);

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		assertEquals(2, operations.size());
		final String tableSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		final String indexSql = operations.get(1).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(!tableSql.contains("UNIQUE"), tableSql);
		assertTrue(indexSql.contains(
				"CREATE UNIQUE INDEX IF NOT EXISTS UK_USERS_EMAIL ON USERS ( EMAIL )"),
				indexSql);
	}

	@Test
	void testRejectUnnamedUniqueConstraint() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320);
		table.getColumns().add(id);
		table.getColumns().add(email);
		table.getConstraints().addPrimaryKeyConstraint("PK_USERS", id);
		table.getConstraints().addUniqueConstraint(null, email);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}

	@Test
	void testCreateNullFilteredStoringIndex() {
		final Table table = new Table("USERS");
		table.setDialect(dialect);
		final Column email = new Column("EMAIL")
				.setDataType(DataType.VARCHAR).setLength(320);
		final Column displayName = new Column("DISPLAY_NAME")
				.setDataType(DataType.VARCHAR).setLength(100);
		table.getColumns().add(email);
		table.getColumns().add(displayName);
		final Index index = new Index("IDX_USERS_EMAIL", email)
				.setUnique(true);
		index.getIncludes().add(displayName);
		index.getSpecifics().put(
				SpannerCreateIndexFactory.IS_NULL_FILTERED, true);
		table.getIndexes().add(index);

		final String sql = sqlFactoryRegistry.createSql(index, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"CREATE UNIQUE NULL_FILTERED INDEX IF NOT EXISTS IDX_USERS_EMAIL ON USERS ( EMAIL ) STORING ( DISPLAY_NAME )"),
				sql);
	}

	@Test
	void testAllowCommitTimestampColumn() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column committedAt = new Column("COMMITTED_AT")
				.setDataType(DataType.TIMESTAMP).setNotNull(true);
		committedAt.getSpecifics().put(
				SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		table.getColumns().add(id);
		table.getColumns().add(committedAt);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"COMMITTED_AT TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)"),
				sql);
	}

	@Test
	void testRejectCommitTimestampOnNonTimestampColumn() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		id.getSpecifics().put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP,
				true);
		table.getColumns().add(id);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}

	@Test
	void testOnUpdateExpression() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column updatedAt = new Column("UPDATED_AT")
				.setDataType(DataType.TIMESTAMP)
				.setDefaultValue("(PENDING_COMMIT_TIMESTAMP())")
				.setOnUpdate("PENDING_COMMIT_TIMESTAMP()");
		updatedAt.getSpecifics().put(
				SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
		table.getColumns().add(id);
		table.getColumns().add(updatedAt);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"DEFAULT (PENDING_COMMIT_TIMESTAMP())"), sql);
		assertTrue(sql.contains(
				"ON UPDATE (PENDING_COMMIT_TIMESTAMP())"), sql);
		assertTrue(sql.contains(
				"OPTIONS (allow_commit_timestamp=true)"), sql);
	}

	@Test
	void testBitReversedIdentity() {
		final Table table = new Table("SINGERS");
		table.setDialect(dialect);
		final Column id = new Column("SINGER_ID")
				.setDataType(DataType.BIGINT)
				.setIdentity(true)
				.setIdentityGenerationType(IdentityGenerationType.ByDefault)
				.setIdentityStartValue(1000);
		id.getSpecifics().put(
				SpannerSqlBuilder.IDENTITY_BIT_REVERSED_POSITIVE, true);
		id.getSpecifics().put(
				SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, 100L);
		id.getSpecifics().put(
				SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 199L);
		table.getColumns().add(id);
		table.getConstraints().addPrimaryKeyConstraint("PK_SINGERS", id);

		final String sql = sqlFactoryRegistry.createSql(table, SqlType.CREATE)
				.get(0).getSqlText().replaceAll("\\s+", " ");
		assertTrue(sql.contains(
				"SINGER_ID INT64 GENERATED BY DEFAULT AS IDENTITY"), sql);
		assertTrue(sql.contains(
				"IDENTITY (BIT_REVERSED_POSITIVE START COUNTER WITH 1000 "
				+ "SKIP RANGE 100, 199 )"), sql);
	}

	@Test
	void testRejectInvalidIdentityOptions() {
		final Table table = new Table("SINGERS");
		table.setDialect(dialect);
		final Column id = new Column("SINGER_ID")
				.setDataType(DataType.VARCHAR)
				.setLength(36)
				.setIdentity(true);
		table.getColumns().add(id);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));

		id.setDataType(DataType.BIGINT);
		id.getSpecifics().put(
				SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MIN, 200L);
		id.getSpecifics().put(
				SpannerSqlBuilder.IDENTITY_SKIP_RANGE_MAX, 100L);
		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}

	@Test
	void testCreateVectorIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		final Column embedding = new Column("EMBEDDING")
				.setDataType(DataType.REAL).setArrayDimension(1);
		embedding.getSpecifics().put(SpannerSqlBuilder.VECTOR_LENGTH, 768);
		final Column category = new Column("CATEGORY")
				.setDataType(DataType.VARCHAR).setLength(100);
		table.getColumns().add(embedding);
		table.getColumns().add(category);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", embedding)
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine)
				.setWhere("EMBEDDING IS NOT NULL");
		index.getIncludes().add(category);
		index.getSpecifics().put(SpannerCreateIndexFactory.TREE_DEPTH, 3);
		index.getSpecifics().put(SpannerCreateIndexFactory.NUM_BRANCHES, 100);
		index.getSpecifics().put(SpannerCreateIndexFactory.NUM_LEAVES, 1000);
		table.getIndexes().add(index);

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		final String tableSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		final String indexSql = operations.get(1).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(tableSql.contains(
				"EMBEDDING ARRAY<FLOAT32>"), tableSql);
		assertTrue(tableSql.contains("vector_length=>768"), tableSql);
		assertTrue(indexSql.contains(
				"CREATE VECTOR INDEX IF NOT EXISTS IDX_DOCUMENTS_EMBEDDING"),
				indexSql);
		assertTrue(indexSql.contains("STORING"), indexSql);
		assertTrue(indexSql.contains("EMBEDDING IS NOT NULL"), indexSql);
		assertTrue(indexSql.contains("distance_type = 'COSINE'"), indexSql);
		assertTrue(indexSql.contains("tree_depth = 3"), indexSql);
		assertTrue(indexSql.contains("num_branches = 100"), indexSql);
		assertTrue(indexSql.contains("num_leaves = 1000"), indexSql);
	}

	@Test
	void testRejectInvalidVectorIndex() {
		final Table table = new Table("DOCUMENTS");
		table.setDialect(dialect);
		final Column embedding = new Column("EMBEDDING")
				.setDataType(DataType.REAL).setArrayDimension(1);
		embedding.getSpecifics().put(SpannerSqlBuilder.VECTOR_LENGTH, 128);
		table.getColumns().add(embedding);
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING", embedding)
				.setIndexType(IndexType.Vector)
				.setVectorDistanceType(VectorDistanceType.Cosine);
		index.getSpecifics().put(SpannerCreateIndexFactory.TREE_DEPTH, 2);
		index.getSpecifics().put(SpannerCreateIndexFactory.NUM_BRANCHES, 10);
		table.getIndexes().add(index);

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}

	@Test
	void testCreateSearchIndexAndTokenListColumn() {
		final Table table = new Table("ARTICLES");
		table.setDialect(dialect);
		final Column body = new Column("BODY")
				.setDataType(DataType.VARCHAR).setLength(1000);
		final Column bodyTokens = new Column("BODY_TOKENS")
				.setDataType(DataType.OTHER)
				.setDataTypeName("TOKENLIST")
				.setFormula("TOKENIZE_FULLTEXT(BODY)")
				.setHidden(true);
		final Column title = new Column("TITLE")
				.setDataType(DataType.VARCHAR).setLength(200);
		table.getColumns().add(body);
		table.getColumns().add(bodyTokens);
		table.getColumns().add(title);
		final Index index = new Index("IDX_ARTICLES_SEARCH", bodyTokens)
				.setIndexType(IndexType.FullText)
				.setWhere("BODY_TOKENS IS NOT NULL");
		index.getIncludes().add(title);
		index.getSpecifics().put(
				SpannerCreateIndexFactory.SORT_ORDER_SHARDING, false);
		table.getIndexes().add(index);

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		final String tableSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		final String indexSql = operations.get(1).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(tableSql.contains(
				"BODY_TOKENS TOKENLIST AS"), tableSql);
		assertTrue(tableSql.contains(
				"TOKENIZE_FULLTEXT(BODY)"), tableSql);
		assertTrue(tableSql.contains("HIDDEN"), tableSql);
		assertTrue(indexSql.contains(
				"CREATE SEARCH INDEX IDX_ARTICLES_SEARCH ON ARTICLES"),
				indexSql);
		assertTrue(indexSql.contains("STORING"), indexSql);
		assertTrue(indexSql.contains("BODY_TOKENS IS NOT NULL"), indexSql);
		assertTrue(indexSql.contains(
				"sort_order_sharding = false"), indexSql);
	}

	@Test
	void testTableColumnAndIndexStorageOptions() {
		final Table table = new Table("EVENTS");
		table.setDialect(dialect);
		table.getSpecifics().put(
				SpannerCreateTableFactory.LOCALITY_GROUP, "hot");
		table.getSpecifics().put(
				SpannerCreateTableFactory.COLUMNAR_POLICY, "columnar");
		table.getSpecifics().put(
				SpannerCreateTableFactory.FULLTEXT_DICTIONARY_TABLE, true);
		table.getSpecifics().put(
				SpannerCreateTableFactory.FULLTEXT_DICTIONARY_STALENESS,
				"15m");
		final Column id = new Column("ID").setDataType(DataType.BIGINT);
		final Column payload = new Column("PAYLOAD")
				.setDataType(DataType.VARCHAR).setLength(1000);
		payload.getSpecifics().put(
				SpannerSqlBuilder.LOCALITY_GROUP, "cold");
		table.getColumns().add(id);
		table.getColumns().add(payload);
		table.getConstraints().addPrimaryKeyConstraint("PK_EVENTS", id);
		final Index index = new Index("IDX_EVENTS_PAYLOAD", payload);
		index.getSpecifics().put(
				SpannerCreateIndexFactory.LOCALITY_GROUP, "hot");
		index.getSpecifics().put(
				SpannerCreateIndexFactory.COLUMNAR_POLICY, "columnar");
		table.getIndexes().add(index);

		final var operations = sqlFactoryRegistry.createSql(table,
				SqlType.CREATE);
		final String tableSql = operations.get(0).getSqlText()
				.replaceAll("\\s+", " ");
		final String indexSql = operations.get(1).getSqlText()
				.replaceAll("\\s+", " ");
		assertTrue(tableSql.contains("locality_group = 'cold'"), tableSql);
		assertTrue(tableSql.contains("locality_group = 'hot'"), tableSql);
		assertTrue(tableSql.contains(
				"columnar_policy = 'columnar'"), tableSql);
		assertTrue(tableSql.contains(
				"fulltext_dictionary_table = true"), tableSql);
		assertTrue(tableSql.contains(
				"fulltext_dictionary_staleness = '15m'"), tableSql);
		assertTrue(indexSql.contains("locality_group = 'hot'"), indexSql);
		assertTrue(indexSql.contains(
				"columnar_policy = 'columnar'"), indexSql);
	}

	@Test
	void testRejectSearchIndexOnNonTokenListColumn() {
		final Table table = new Table("ARTICLES");
		table.setDialect(dialect);
		final Column body = new Column("BODY")
				.setDataType(DataType.VARCHAR).setLength(1000);
		table.getColumns().add(body);
		table.getIndexes().add(new Index("IDX_ARTICLES_SEARCH", body)
				.setIndexType(IndexType.FullText));

		assertThrows(IllegalArgumentException.class,
				() -> sqlFactoryRegistry.createSql(table, SqlType.CREATE));
	}
}
