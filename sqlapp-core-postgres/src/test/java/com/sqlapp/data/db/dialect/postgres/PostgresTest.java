/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core-postgres.
 *
 * sqlapp-core-postgres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-postgres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-postgres.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.AbstractLengthType;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.resolver.PostgresDialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

public class PostgresTest {

	private Dialect dialect = DialectHolder.defaultDialect;

	@Test
	public void testInsertRowsReturningSupportStartsAt82() {
		Dialect postgres81 = new Postgres(() -> null);
		Dialect postgres82 = new Postgres82(() -> null);
		Table table = new Table("tableA");
		Column identity = table.newColumn().setName("id");

		assertFalse(postgres81.supportsInsertReturningResultSet());
		assertTrue(postgres82.supportsInsertReturningResultSet());
		assertEquals("INSERT INTO \"tableA\" VALUES (?, ?)\nRETURNING id",
				postgres82.handleInsertReturningSql(table, identity, "INSERT INTO \"tableA\" VALUES (?, ?)"));
		assertEquals(65535, postgres82.getMaxStatementParameterCount());
	}

	@Test
	public void testDialectHolder82IsPostgres82() {
		assertSame(Postgres82.class, DialectHolder.postgreSQL82.getClass());
	}

	@Test
	public void testWithClauseSupportStartsAt84() {
		assertFalse(DialectHolder.postgreSQL82.supportsWith());
		assertFalse(DialectHolder.postgreSQL82.supportsWithRecursive());
		assertFalse(DialectHolder.postgreSQL83.supportsWith());
		assertFalse(DialectHolder.postgreSQL83.supportsWithRecursive());
		assertTrue(DialectHolder.postgreSQL84.supportsWith());
		assertTrue(DialectHolder.postgreSQL84.supportsWithRecursive());
		assertTrue(DialectHolder.postgreSQL180.supportsWith());
		assertTrue(DialectHolder.postgreSQL180.supportsWithRecursive());
	}

	@Test
	public void testUuidLiteral() {
		Column column = new Column("id").setDataType(DataType.UUID);
		UUID uuid = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
		assertEquals("'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid",
				DialectHolder.postgreSQL180.getSqlValueDefinition(column, uuid));
	}

	@Test
	public void testBitAndByteStringLiterals() {
		Dialect pg = DialectHolder.postgreSQL180;
		assertEquals("B'0101'", pg.getSqlValueDefinition(new Column("b").setDataType(DataType.BIT), "0101"));
		assertEquals("B'0101'", pg.getSqlValueDefinition(new Column("vb").setDataType(DataType.VARBIT), "0101"));
		byte[] value = new byte[] { (byte) 0x1f, (byte) 0xa0 };
		assertEquals("decode('1fa0', 'hex')",
				pg.getSqlValueDefinition(new Column("bytes").setDataType(DataType.VARBINARY), value));
		assertEquals("true", pg.getSqlValueDefinition(new Column("flag").setDataType(DataType.BOOLEAN), Boolean.TRUE));
		assertEquals("false",
				pg.getSqlValueDefinition(new Column("flag").setDataType(DataType.BOOLEAN), Boolean.FALSE));
	}

	@Test
	public void testBitAndByteStringTypeDefinitions() {
		assertEquals("BIT(1)", typeDefinition(new Column("b").setDataType(DataType.BIT)));
		assertEquals("BIT(16)", typeDefinition(new Column("b").setDataType(DataType.BIT).setLength(16)));
		assertEquals("VARBIT", typeDefinition(new Column("vb").setDataType(DataType.VARBIT)));
		assertEquals("VARBIT(16)", typeDefinition(new Column("vb").setDataType(DataType.VARBIT).setLength(16)));
		// Byte strings are bytea, which has no declared length.
		for (DataType type : new DataType[] { DataType.BINARY, DataType.VARBINARY, DataType.LONGVARBINARY,
				DataType.BLOB }) {
			assertEquals("BYTEA", typeDefinition(new Column("bytes").setDataType(type)), type.toString());
			assertEquals("BYTEA", typeDefinition(new Column("bytes").setDataType(type).setLength(16)), type.toString());
		}
	}

	@Test
	public void testReadsBitByteaAndBooleanTypeNames() {
		Object[][] cases = { { "bit", DataType.BIT, 1L }, { "bit(8)", DataType.BIT, 8L },
				{ "bit varying", DataType.VARBIT, null }, { "bit varying(12)", DataType.VARBIT, 12L },
				{ "varbit(3)", DataType.VARBIT, 3L }, { "bytea", DataType.VARBINARY, null },
				{ "boolean", DataType.BOOLEAN, null } };
		for (Object[] c : cases) {
			Column column = getColumn((String) c[0]);
			assertEquals(c[1], column.getDataType(), (String) c[0]);
			if (c[2] != null)
				assertEquals(c[2], column.getLength(), (String) c[0]);
		}
		// Bit string lengths survive the XML round trip.
		Table table = new Table("t");
		table.getColumns().add(new Column("b").setDataType(DataType.BIT).setLength(7));
		table.getColumns().add(new Column("vb").setDataType(DataType.VARBIT).setLength(7));
		table.getColumns().add(new Column("u").setDataType(DataType.VARBIT));
		try {
			Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			assertEquals(Long.valueOf(7), restored.getColumns().get("b").getLength());
			assertEquals(Long.valueOf(7), restored.getColumns().get("vb").getLength());
			assertEquals(null, restored.getColumns().get("u").getLength());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		Column array = getColumn("bit(4)[]");
		assertEquals(DataType.BIT, array.getDataType());
		assertEquals(1, array.getArrayDimension());
	}

	private String typeDefinition(Column column) {
		return DialectHolder.postgreSQL180.createSqlBuilder().addTypeDefinition(column).toString();
	}

	@Test
	public void testSequenceNextValEscapesQuotes() {
		assertEquals("select nextval('\"public\".\"o''dd\"')", dialect.getSequenceNextValString("\"public\".\"o'dd\""));
	}

	@Test
	public void testSystemUserIsQuoted() {
		assertTrue(DialectHolder.postgreSQL160.needQuote("system_user"));
	}

	@Test
	public void testCharacterMaxLength() {
		assertEquals(Long.valueOf(10485760L),
				((AbstractLengthType<?>) dialect.getDbDataTypes().getDbType(DataType.CHAR)).getMaxLength());
		assertEquals(Long.valueOf(10485760L),
				((AbstractLengthType<?>) dialect.getDbDataTypes().getDbType(DataType.VARCHAR, 50000L)).getMaxLength());
	}

	@Test
	public void testArrayTypes() {
		Column column = getColumn("varchar(10)[]");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals(Long.valueOf(10), column.getLength());
		assertEquals(1, column.getArrayDimension());
		column = getColumn("int4range[]");
		assertEquals(DataType.RANGE, column.getDataType());
		assertEquals(1, column.getArrayDimension());
		assertTrue(dialect.getDbDataTypes().getDbType(DataType.VARCHAR, 10L).isSupportsArray());
		assertTrue(dialect.getDbDataTypes().getDbType(DataType.RANGE).isSupportsArray());
		assertFalse(dialect.getDbDataTypes().getDbType(DataType.SERIAL).isSupportsArray());
		assertFalse(dialect.getDbDataTypes().getDbType(DataType.BIGSERIAL).isSupportsArray());
		Dialect postgres94 = DialectHolder.postgreSQL94;
		assertTrue(postgres94.getDbDataTypes().getDbType(DataType.JSON).isSupportsArray());
		assertTrue(postgres94.getDbDataTypes().getDbType(DataType.JSONB).isSupportsArray());
		assertFalse(postgres94.getDbDataTypes().getDbType(DataType.SMALLSERIAL).isSupportsArray());
		assertTrue(DialectHolder.postgreSQL140.getDbDataTypes().getDbType(DataType.MULTIRANGE).isSupportsArray());
	}

	@Test
	public void testUnknown9xResolvesTo96() {
		assertSame(DialectHolder.postgreSQL96,
				new PostgresDialectResolver.PostgresVersionResolver().getDialect(9, 7, null));
	}

	@Test
	public void testToType() {
		Column column = getColumn("TIMESTAMP(6) WITH TIME ZONE");
		assertEquals(DataType.TIMESTAMP_WITH_TIMEZONE, column.getDataType());
		assertEquals(Long.valueOf(6), column.getLength());
		column = getColumn("INTERVAL HOUR");
		assertEquals(DataType.INTERVAL_HOUR, column.getDataType());
		column = getColumn("POINT");
		assertEquals(DataType.POINT, column.getDataType());
		column = getColumn("CIRCLE");
		assertEquals(DataType.CIRCLE, column.getDataType());
	}

	@Test
	public void testDbClob() {
		Column column = getColumn("TEXT");
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals("TEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

	@Test
	public void testDbClob2() {
		Column column = new Column();
		dialect.setDbType("TEXT", null, null, column);
		assertEquals(DataType.VARCHAR, column.getDataType());
		assertEquals("TEXT", column.getDataTypeName());
		assertEquals(Long.valueOf(CommonUtils.LEN_1GB), column.getLength());
	}

	protected Column getColumn(String dataTypeName) {
		Column column = new Column();
		column.setDialect(dialect);
		column.setDataTypeName(dataTypeName);
		return column;
	}
}
