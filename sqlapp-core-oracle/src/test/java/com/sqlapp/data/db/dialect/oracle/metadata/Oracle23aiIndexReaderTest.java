/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.oracle.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.VectorDistanceType;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.JdbcQueryHandler;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Oracle23aiIndexReaderTest {

	@Test
	void vectorDetailsAreBatchedAndRoutedByOwnerAndName() throws Exception {
		for (IndexType type : List.of(
				IndexType.Vector, IndexType.Domain)) {
			var queries = new AtomicInteger();
			var reader = new Oracle23aiIndexReader(DialectHolder.oracle23aiDialect) {
				@Override
				protected JdbcQueryHandler execute(Connection connection,
						SqlNode node, ParametersContext context,
						ResultSetNextHandler handler) {
					queries.incrementAndGet();
					assertEquals(2, ((Collection<?>)context.get("schemaName")).size());
					for (String owner : List.of("A", "B", "UNREQUESTED")) {
						try {
							handler.handleResultSetNext(new ExResultSet(null) {
								@Override
								public String getString(String column) {
									return switch (column) {
									case "OWNER" -> owner;
									case "INDEX_NAME" -> "IX";
									case "IDX_PARAMS" -> "{\"accuracy\":" + (owner.equals("A") ? 90 : 95) + "}";
									case "IXO_CLASS" -> "LEXER";
									case "IXO_OBJECT" -> owner + "_LEXER";
									default -> null;
									};
								}
							});
						} catch (SQLException e) { throw new RuntimeException(e); }
					}
					return null;
				}
			};
			Index first = new Index("IX").setSchemaName("A").setIndexType(type);
			Index second = new Index("IX").setSchemaName("B").setIndexType(type);
			reader.setMetadataDetail(null, new ParametersContext(), List.of(first, second));
			reader.setMetadataDetail(null, first);
			reader.setMetadataDetail(null, second);
			assertEquals(1, queries.get(), "Collection metadata must not be reread by per-object hooks");
			if (type == IndexType.Vector) {
				assertEquals("90", first.getSpecifics().get(Oracle23aiIndexReader.TARGET_ACCURACY));
				assertEquals("95", second.getSpecifics().get(Oracle23aiIndexReader.TARGET_ACCURACY));
			} else {
				assertEquals("A_LEXER", first.getSpecifics().get("LEXER"));
				assertEquals("B_LEXER", second.getSpecifics().get("LEXER"));
				assertEquals(IndexType.Vector, first.getIndexType());
			}
		}
	}

	@Test
	void testApplyVectorIndexParameters() {
		final Index index = new Index("IDX_DOCUMENTS_EMBEDDING");

		Oracle23aiIndexReader.applyVectorIndexParameters(index, """
				{
				  "type": "HNSW",
				  "num_neighbors": 32,
				  "efConstruction": 300,
				  "distance": "COSINE",
				  "accuracy": 95,
				  "vector_type": "FLOAT32",
				  "vector_dimension": 384,
				  "degree_of_parallelism": 4,
				  "indexed_col": "EMBED_VECTOR"
				}
				""");

		assertEquals(VectorDistanceType.Cosine, index.getVectorDistanceType());
		assertEquals("HNSW", index.getSpecifics().get(Oracle23aiIndexReader.ORGANIZATION));
		assertEquals("32", index.getSpecifics().get(Oracle23aiIndexReader.NEIGHBORS));
		assertEquals("300", index.getSpecifics().get(Oracle23aiIndexReader.EFCONSTRUCTION));
		assertEquals("95", index.getSpecifics().get(Oracle23aiIndexReader.TARGET_ACCURACY));
		assertEquals("4", index.getSpecifics().get(Oracle23aiIndexReader.PARALLEL));
		assertEquals("FLOAT32", index.getSpecifics().get("VECTOR_TYPE"));
		assertEquals("384", index.getSpecifics().get("VECTOR_DIMENSION"));
		assertEquals("EMBED_VECTOR", index.getSpecifics().get("INDEXED_COLUMN"));
	}

	@Test
	void testOnlyUnavailableOrUnauthorizedErrorsCanBeSkipped() {
		assertTrue(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("table or view does not exist", "42000", 942))));
		assertTrue(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("insufficient privileges", "42000", 1031))));
		assertFalse(Oracle23aiIndexReader.isVectorIndexDetailUnavailable(
				new RuntimeException(new SQLException("invalid identifier", "42000", 904))));
	}
}
