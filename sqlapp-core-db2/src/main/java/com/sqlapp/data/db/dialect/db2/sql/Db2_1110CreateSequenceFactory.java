/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import java.util.*;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.db2.util.*;
import com.sqlapp.data.db.sql.*;
import com.sqlapp.data.schemas.Sequence;

/** Sequence definitions recreate configuration, not live allocation state. */
public class Db2_1110CreateSequenceFactory extends Db2CreateSequenceFactory {
	@Override
	public List<SqlOperation> createSql(Sequence sequence) {
		if (sequence.getDefinition().isEmpty())
			return super.createSql(sequence);
		List<SqlOperation> result = new ArrayList<>();
		for (String sql : Db2DefinitionSql.statements(sequence.getDefinition(), getDialect()))
			addSql(result, createSqlBuilder()._add(sql), SqlType.CREATE, sequence);
		addOtherDefinitions(sequence, result);
		return result;
	}

	@Override
	protected void addCreateObject(Sequence sequence, Db2SqlBuilder builder) {
		builder._add("CREATE SEQUENCE " + name(sequence, getOptions().isDecorateSchemaName()));
		if (sequence.getDataType() == DataType.OTHER) {
			builder._add(" AS " + Db2CreationContext.qualified(sequence.getSpecifics().get("SEQUENCE_TYPE_SCHEMA"),
					sequence.getSpecifics().get("SEQUENCE_TYPE_NAME")));
		} else if (sequence.getDataType() != null || sequence.getDataTypeName() != null) {
			if (!Set.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT, DataType.DECIMAL)
					.contains(sequence.getDataType() == null ? DataType.OTHER : sequence.getDataType()))
				throw new IllegalArgumentException(
						"Db2 sequence requires an integer/decimal type or complete definition");
			if (sequence.getDataType() == DataType.DECIMAL && sequence.getPrecision() != null
					&& (sequence.getPrecision() < 1 || sequence.getPrecision() > 31))
				throw new IllegalArgumentException("Db2 decimal sequence precision must be between 1 and 31");
			addDataType(sequence, builder);
		}
		if (sequence.getStartValue() != null)
			addStartWith(sequence, builder);
		if (sequence.getIncrementBy() != null) {
			if (sequence.getIncrementBy().signum() == 0)
				throw new IllegalArgumentException("Db2 sequence increment must not be zero");
			addIncrementBy(sequence, builder);
		}
		if (sequence.getMinValue() != null && sequence.getMaxValue() != null
				&& sequence.getMinValue().compareTo(sequence.getMaxValue()) >= 0)
			throw new IllegalArgumentException("Db2 sequence minimum must be less than maximum");
		if (sequence.getMaxValue() != null)
			addMaxValue(sequence, builder);
		if (sequence.getMinValue() != null)
			addMinValue(sequence, builder);
		builder._add(sequence.isCycle() ? " CYCLE" : " NO CYCLE");
		if (sequence.getCacheSize() != null)
			addCache(sequence, builder);
		else
			builder._add(" NO CACHE");
		builder._add(sequence.isOrder() ? " ORDER" : " NO ORDER");
	}

	@Override
	protected void addOtherDefinitions(Sequence sequence, List<SqlOperation> result) {
		if (sequence.getRemarks() != null && !sequence.getRemarks().isEmpty())
			addSql(result,
					createSqlBuilder()
							._add("COMMENT ON SEQUENCE " + name(sequence, getOptions().isDecorateSchemaName()) + " IS ")
							.sqlChar(sequence.getRemarks()),
					SqlType.SET_COMMENT, sequence);
	}

	static String name(Sequence sequence, boolean decorate) {
		return decorate && sequence.getSchemaName() != null
				? Db2CreationContext.qualified(sequence.getSchemaName(), sequence.getName())
				: Db2CreationContext.identifier(sequence.getName());
	}
}
