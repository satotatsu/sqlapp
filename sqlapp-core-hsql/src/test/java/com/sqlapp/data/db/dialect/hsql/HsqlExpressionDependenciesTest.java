/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class HsqlExpressionDependenciesTest {
	@Test
	void typeUseDiscoveryPreservesQuotedNamesAndIgnoresLiteralOrCommentContents() {
		assertTrue(HsqlExpressionDependencies.mentionsIdentifier("CAST(V AS \"Other.Schema\".\"Mixed Type\")",
				"Other.Schema", "Mixed Type"));
		assertFalse(HsqlExpressionDependencies.mentionsIdentifier("CAST(V AS OTHER.D)", "APP", "D"));
		assertFalse(HsqlExpressionDependencies.mentionsIdentifier("RETURN 'APP.D' /* APP.D */ -- D\n", "APP", "D"));
		assertTrue(HsqlExpressionDependencies.mentionsIdentifier("RETURNS D", "APP", "D"));
	}

	@Test
	void preservesQuotedIdentityAndIgnoresLiteralAndCommentContents() {
		var references = HsqlExpressionDependencies
				.references("'FAKE.F(1)' || \"Schema.With.Dot\".\"Fn\"\"Name\" /* X.F() */ (2) -- Y.F()\n + lower(3)");
		assertEquals(2, references.size());
		assertEquals(List.of("Schema.With.Dot", "Fn\"Name"), references.get(0).name());
		assertEquals(List.of("LOWER"), references.get(1).name());
		assertTrue(HsqlExpressionDependencies.references("'it''s F()' /* nested /* F() */ */").isEmpty());
	}

	@Test
	void detectsSequenceReferencesAndRejectsMalformedExpressions() {
		var references = HsqlExpressionDependencies
				.references("NEXT VALUE FOR app.seq + CURRENT VALUE FOR \"Mixed Schema\".\"Seq.Name\"");
		assertEquals(2, references.size());
		assertTrue(references.stream().allMatch(HsqlExpressionDependencies.Reference::sequence));
		assertEquals(List.of("APP", "SEQ"), references.get(0).name());
		assertEquals(List.of("Mixed Schema", "Seq.Name"), references.get(1).name());
		assertThrows(IllegalArgumentException.class, () -> HsqlExpressionDependencies.references("'unfinished"));
		assertThrows(IllegalArgumentException.class, () -> HsqlExpressionDependencies.references("/* unfinished"));
	}
}
