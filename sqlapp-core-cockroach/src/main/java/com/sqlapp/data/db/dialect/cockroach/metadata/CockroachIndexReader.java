/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.metadata;

import java.sql.*;
import java.util.*;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresIndexReader;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

/**
 * Retains vendor index syntax as well as ordinary modeled
 * keys/includes/predicates.
 */
public class CockroachIndexReader extends PostgresIndexReader {
	public CockroachIndexReader(Dialect dialect) {
		super(dialect);
	}

	@Override
	protected void readDefinition(Index index, String definition) {
		if (definition == null || definition.isBlank())
			throw new IllegalStateException("CockroachDB index definition missing: " + index.getName());
		index.setDefinition(definition);
	}
}
