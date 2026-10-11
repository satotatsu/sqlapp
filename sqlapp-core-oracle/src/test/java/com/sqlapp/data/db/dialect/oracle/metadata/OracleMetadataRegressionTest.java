/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.metadata;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.schemas.Deferrability;

class OracleMetadataRegressionTest {
	@Test
	void deferredAndDeferrableAreDifferentCatalogFields() {
		assertEquals(Deferrability.InitiallyDeferred, OracleMetadataUtils.getDeferrability("DEFERRABLE", "DEFERRED"));
		assertEquals(Deferrability.InitiallyImmediate, OracleMetadataUtils.getDeferrability("DEFERRABLE", "IMMEDIATE"));
		assertEquals(Deferrability.NotDeferrable, OracleMetadataUtils.getDeferrability("NOT DEFERRABLE", "IMMEDIATE"));
		assertNull(OracleMetadataUtils.getDeferrability(null, null));
	}
}
