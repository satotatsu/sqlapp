/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.metadata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.firebird.DialectHolder;
import com.sqlapp.data.db.metadata.ReaderOptions;
import com.sqlapp.jdbc.ExResultSet;

class FirebirdOwnerMetadataTest {
	private ExResultSet row(String owner) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return "RDB$OWNER_NAME".equals(key) ? owner : null;
			}

			@Override
			public Object getObject(String key) {
				return null;
			}

			@Override
			public boolean getBoolean(String key) {
				return false;
			}

			@Override
			public int getInt(String key) {
				return 0;
			}

			@Override
			public long getLong(String key) {
				return 0L;
			}
		};
	}

	@Test
	void readsOwnersWithoutSourceAndPreservesCase() throws Exception {
		var dialect = DialectHolder.defaultDialect20;
		var options = new ReaderOptions();
		options.setReadStatement(false);
		var tables = new FirebirdTableReader(dialect);
		var views = new FirebirdViewReader(dialect);
		views.setReaderOptions(options);
		var procedures = new FirebirdProcedureReader(dialect);
		procedures.setReaderOptions(options);
		var row = row("  Mixed Owner  ");
		assertEquals("Mixed Owner", tables.createTable(row).getSpecifics().get("firebird.owner"));
		assertEquals("Mixed Owner", views.createTable(row).getSpecifics().get("firebird.owner"));
		assertEquals("Mixed Owner", procedures.createProcedure(row).getSpecifics().get("firebird.owner"));
	}

	@Test
	void unknownOwnersRemainAbsent() throws Exception {
		var dialect = DialectHolder.defaultDialect20;
		var views = new FirebirdViewReader(dialect);
		views.setReaderOptions(new ReaderOptions());
		var procedures = new FirebirdProcedureReader(dialect);
		procedures.setReaderOptions(new ReaderOptions());
		assertFalse(
				new FirebirdTableReader(dialect).createTable(row(null)).getSpecifics().containsKey("firebird.owner"));
		assertFalse(views.createTable(row(null)).getSpecifics().containsKey("firebird.owner"));
		assertFalse(procedures.createProcedure(row(null)).getSpecifics().containsKey("firebird.owner"));
	}

	@Test
	void modernFunctionsAndPackagePartsRetainOwnersWithoutSource() throws Exception {
		var dialect = DialectHolder.defaultDialect30;
		var options = new ReaderOptions();
		options.setReadStatement(false);
		var functions = new Firebird30FunctionReader(dialect);
		functions.setReaderOptions(options);
		var headers = new Firebird30PackageReader(dialect);
		headers.setReaderOptions(options);
		var bodies = new Firebird30PackageBodyReader(dialect);
		bodies.setReaderOptions(options);
		for (String owner : new String[] { "  Mixed Owner  ", null }) {
			java.util.List<com.sqlapp.data.schemas.AbstractNamedObject<?>> objects = java.util.List.of(
					functions.createFunction(row(owner)), headers.createPackage(row(owner)),
					bodies.createPackageBody(row(owner)));
			for (var object : objects) {
				assertEquals(owner == null ? null : "Mixed Owner", object.getSpecifics().get("firebird.owner"));
				assertEquals(owner != null, object.getSpecifics().containsKey("firebird.owner"));
			}
		}
	}

	@Test
	void legacyFunctionsDoNotAccessModernOwnerColumn() throws Exception {
		var row = new ExResultSet(null) {
			@Override
			public String getString(String key) {
				if ("RDB$OWNER_NAME".equals(key))
					throw new AssertionError("Owner column requires Firebird 3.0");
				return null;
			}
		};
		var functions = new FirebirdFunctionReader(DialectHolder.defaultDialect25);
		functions.setReaderOptions(new ReaderOptions());
		assertFalse(functions.createFunction(row).getSpecifics().containsKey("firebird.owner"));
	}

	@Test
	void domainsAndSequencesRetainModernOwners() throws Exception {
		var dialect = DialectHolder.defaultDialect30;
		for (String owner : new String[] { "  Mixed Owner  ", null }) {
			var domain = new Firebird30DomainReader(dialect).createDomain(domainRow(owner));
			var sequence = new Firebird30SequenceReader(dialect).createSequence(row(owner));
			assertEquals(owner == null ? null : "Mixed Owner", domain.getSpecifics().get("firebird.owner"));
			assertEquals(owner == null ? null : "Mixed Owner", sequence.getSpecifics().get("firebird.owner"));
			assertEquals(owner != null, domain.getSpecifics().containsKey("firebird.owner"));
			assertEquals(owner != null, sequence.getSpecifics().containsKey("firebird.owner"));
		}
	}

	private ExResultSet domainRow(String owner) {
		return new ExResultSet(null) {
			@Override
			public String getString(String key) {
				return "RDB$OWNER_NAME".equals(key) ? owner : null;
			}

			@Override
			public int getInt(String key) {
				return "RDB$FIELD_TYPE".equals(key) ? 8 : 0;
			}
		};
	}

	@Test
	void schemaSelectsVersionedDomainAndSequenceReaders() {
		for (var dialect : java.util.List.of(DialectHolder.defaultDialect30, DialectHolder.defaultDialect40,
				DialectHolder.defaultDialect50)) {
			var schema = dialect.getCatalogReader().getSchemaReader();
			assertInstanceOf(Firebird30DomainReader.class, schema.getDomainReader());
			assertInstanceOf(Firebird30SequenceReader.class, schema.getSequenceReader());
		}
		var schema = DialectHolder.defaultDialect25.getCatalogReader().getSchemaReader();
		assertFalse(schema.getDomainReader() instanceof Firebird30DomainReader);
		assertFalse(schema.getSequenceReader() instanceof Firebird30SequenceReader);
	}

	@Test
	void legacyDomainsAndSequencesNeverReadOwnerColumn() throws Exception {
		var row = new ExResultSet(null) {
			@Override
			public String getString(String key) {
				if ("RDB$OWNER_NAME".equals(key))
					throw new AssertionError("Owner column requires Firebird 3.0");
				return null;
			}

			@Override
			public int getInt(String key) {
				return "RDB$FIELD_TYPE".equals(key) ? 8 : 0;
			}
		};
		var dialect = DialectHolder.defaultDialect25;
		assertFalse(new FirebirdDomainReader(dialect).createDomain(row).getSpecifics().containsKey("firebird.owner"));
		assertFalse(
				new Firebird20SequenceReader(dialect).createSequence(row).getSpecifics().containsKey("firebird.owner"));
	}
}
