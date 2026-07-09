package com.sqlapp.jdbc.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Sequence;
import com.sqlapp.data.schemas.ValuesGenerator;

public class SequenceValueGenerator implements ValuesGenerator<Object> {

	private final SequenceGenerator sequenceGenerator;

	public SequenceValueGenerator(Connection connection, Dialect dialect, Sequence sequence) {
		sequenceGenerator = new SequenceGenerator(connection, dialect, sequence);
	}

	@Override
	public List<Object> generateValues(int size) throws SQLException {
		return sequenceGenerator.get(size);
	}

	@Override
	public void close() {
		sequenceGenerator.close();
	}
}
