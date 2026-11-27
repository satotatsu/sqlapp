/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferGeneratedKeyChecks;

class TransferGeneratedKeyNoticeTest {
	@Test
	void noticeOnlyAppearsForCodedGeneratedKeyChecksAndDoesNotClaimValidation() {
		final var plain = new StringBuilder();
		TransferGeneratedKeyNotice.append(plain, List.of("Product differences need testing"));
		assertEquals("", plain.toString());
		final var text = new StringBuilder();
		TransferGeneratedKeyNotice.append(text, List.of("Product differences need testing",
				TransferGeneratedKeyChecks.CODE + " Target 'ARCHIVE.CUSTOMERS', column 'ID': counter not checked."));
		assertTrue(text.toString().contains("for 1 column(s)"));
		assertTrue(text.toString().contains("does not verify future generated IDs"));
		assertEquals(List.of("Target 'ARCHIVE.CUSTOMERS', column 'ID': counter not checked."),
				TransferGeneratedKeyChecks.messages(List.of(TransferGeneratedKeyChecks.CODE
						+ " Target 'ARCHIVE.CUSTOMERS', column 'ID': counter not checked.")));
	}
}
