/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.DialectHolder;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.*;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.jdbc.ExResultSet;
import com.sqlapp.jdbc.sql.*;
import com.sqlapp.jdbc.sql.node.SqlNode;

class Db2SequenceRecreationTest {
	private Map<String, String> fields(String type, int precision) {
		var values = new HashMap<String, String>(Map.of("sequence_name", "S\".Q", "schema_name", "App.Q",
				"SEQUENCE_TYPE_SCHEMA", "SYSIBM", "SEQUENCE_TYPE_NAME", type, "PRECISION", Integer.toString(precision),
				"START", "1", "MINVALUE", "1", "MAXVALUE",
				BigInteger.TEN.pow(precision).subtract(BigInteger.ONE).toString(), "INCREMENT", "1", "CACHE", "20"));
		values.putAll(
				Map.of("CYCLE", "N", "ORDER", "Y", "NEXTCACHEFIRSTVALUE", "101", "remarks", "sequence's comment"));
		return values;
	}

	private ExResultSet row(Map<String, String> values) {
		return new ExResultSet(null) {
			@Override
			public String getString(String name) {
				return values.get(name);
			}

			@Override
			public int getInt(String name) {
				return Integer.parseInt(values.getOrDefault(name, "0"));
			}

			@Override
			public BigDecimal getBigDecimal(String name) {
				return values.get(name) == null ? null : new BigDecimal(values.get(name));
			}

			@Override
			public Timestamp getTimestamp(String name) {
				return null;
			}
		};
	}

	private Sequence read(Map<String, String> fields) throws SQLException {
		return new Db2SequenceReader(DialectHolder.Db2_1110Dialect).createSequence(row(fields));
	}

	private List<String> sql(Sequence sequence, SqlType type) throws Exception {
		Sequence restored = SchemaUtils.readXml(new StringReader(sequence.asXml()));
		SqlFactory<Sequence> factory = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(restored,
				type);
		return factory.createSql(restored).stream().map(SqlOperation::getSqlText).toList();
	}

	@Test
	void decimalTypesWithIntegerPrecisionsRetainExactTypeAfterXml() throws Exception {
		for (int precision : List.of(5, 10, 19, 31)) {
			Sequence sequence = read(fields("DECIMAL", precision));
			assertEquals(DataType.DECIMAL, sequence.getDataType());
			assertEquals(precision, sequence.getPrecision());
			var generated = sql(sequence, SqlType.CREATE);
			assertTrue(generated.getFirst().contains("AS DECIMAL(" + precision + ",0)"), generated.toString());
			assertTrue(generated.getFirst().startsWith("CREATE SEQUENCE \"App.Q\".\"S\"\".Q\""));
			assertFalse(generated.getFirst().contains("OR REPLACE"));
			assertTrue(generated.getFirst().contains("NO CYCLE CACHE 20 ORDER"));
			assertEquals("COMMENT ON SEQUENCE \"App.Q\".\"S\"\".Q\" IS 'sequence''s comment'", generated.getLast());
			assertEquals(List.of("DROP SEQUENCE \"App.Q\".\"S\"\".Q\" RESTRICT"), sql(sequence, SqlType.DROP));
		}
	}

	@Test
	void nativeDistinctTypeLargeStartAndCacheBoundaryAreNotLiveLastValue() throws Exception {
		var fields = fields("Id\".Type", 31);
		fields.put("SEQUENCE_TYPE_SCHEMA", "Type.Schema");
		fields.put("START", "9223372036854775808");
		Sequence sequence = read(fields);
		assertEquals(DataType.OTHER, sequence.getDataType());
		assertNull(sequence.getLastValue());
		assertEquals("101", sequence.getSpecifics().get("NEXTCACHEFIRSTVALUE"));
		assertEquals(new BigInteger("9223372036854775808"), sequence.getStartValue());
		String ddl = sql(sequence, SqlType.CREATE).getFirst();
		assertTrue(ddl.contains("AS \"Type.Schema\".\"Id\"\".Type\""), ddl);
		assertTrue(ddl.contains("START WITH 9223372036854775808"), ddl);
		assertFalse(ddl.contains("101"), ddl);
	}

	@Test
	void completeMultilineDefinitionsOwnEmbeddedOptionsAndInvalidModelsFailClearly() throws Exception {
		Sequence sequence = new Sequence("S").setSchemaName("APP").setDataType(DataType.VARCHAR);
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, SqlType.CREATE));
		String ddl = "CREATE SEQUENCE APP.S\n AS BIGINT\n START WITH 10";
		sequence.setDefinition(ddl);
		assertEquals(List.of(ddl), sql(sequence, SqlType.CREATE));
		sequence.setDefinition((String) null).setDataType(DataType.DECIMAL).setPrecision(32);
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, SqlType.CREATE));
		sequence.setPrecision(10).setScale(1);
		SqlFactory<Sequence> direct = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(sequence,
				SqlType.CREATE);
		assertThrows(IllegalArgumentException.class, () -> direct.createSql(sequence));
		sequence.setScale(0).setIncrementBy(BigInteger.ZERO);
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, SqlType.CREATE));
		sequence.setIncrementBy(BigInteger.ONE).setMinValue(BigInteger.TEN).setMaxValue(BigInteger.ONE);
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, SqlType.CREATE));
	}

	@Test
	void typeNameOnlyModelsWorkAndConflictingNamesCannotChangeSemantics() throws Exception {
		for (String type : List.of("SMALLINT", "INTEGER", "INT", "BIGINT", "DECIMAL", "NUMERIC")) {
			Sequence sequence = new Sequence("S").setDataTypeName(type);
			assertTrue(sql(sequence, SqlType.CREATE).getFirst().contains(" AS "));
			assertNull(sequence.getDataType());
		}
		Sequence sequence = new Sequence("S").setDataType(DataType.INT).setDataTypeName("BIGINT");
		assertThrows(IllegalArgumentException.class, () -> sql(sequence, SqlType.CREATE));
	}

	@Test
	void modernTypesUseOneBulkJoinAndHistoricalReaderRetainsOldRepresentation() throws Exception {
		AtomicInteger count = new AtomicInteger();
		var reader = new Db2SequenceReader(DialectHolder.Db2_1110Dialect) {
			@Override
			protected JdbcQueryHandler execute(Connection c, SqlNode node, ParametersContext p,
					ResultSetNextHandler handler) {
				count.incrementAndGet();
				assertTrue(node.toString().contains("LEFT JOIN SYSCAT.DATATYPES"));
				for (int i = 0; i < 20; i++)
					try {
						handler.handleResultSetNext(row(fields("DECIMAL", 5)));
					} catch (SQLException e) {
						throw new IllegalStateException(e);
					}
				return null;
			}
		};
		assertEquals(20, reader.doGetAll(null, new ParametersContext(), new ProductVersionInfo()).size());
		assertEquals(1, count.get());
		var old = new Db2SequenceReader(DialectHolder.Db2_1050Dialect);
		assertFalse(old.getSqlNode(new ProductVersionInfo()).toString().contains("SYSCAT.DATATYPES"));
		assertEquals(DataType.SMALLINT, old.createSequence(row(fields("DECIMAL", 5))).getDataType());
		var missing = fields("DECIMAL", 5);
		missing.remove("SEQUENCE_TYPE_NAME");
		assertThrows(SQLException.class, () -> read(missing));
	}

	@Test
	void changesCannotImplicitlyReplaceOrRestartLiveSequencesThroughSchemaDiff() {
		Sequence before = new Sequence("S").setSchemaName("APP").setStartValue(BigInteger.ONE);
		Sequence after = before.clone().setStartValue(BigInteger.TEN);
		SqlFactory<Sequence> alter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(after,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> alter.createDiffSql(before.diff(after)));
		Schema original = new Schema("APP"), target = new Schema("APP");
		original.getSequences().add(before);
		target.getSequences().add(after);
		SqlFactory<Schema> schemaAlter = DialectHolder.Db2_1110Dialect.createSqlFactoryRegistry().getSqlFactory(target,
				SqlType.ALTER);
		assertThrows(IllegalArgumentException.class, () -> schemaAlter.createDiffSql(original.diff(target)));
	}
}
