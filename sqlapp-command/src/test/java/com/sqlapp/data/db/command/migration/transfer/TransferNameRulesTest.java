/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TransferNameRulesTest {
	@Test
	void preservesNamesByDefaultAndResolvesCaseAndUnicodeSpacesWithoutGlobalSettings() {
		assertEquals("日本語 Customer Name", new TransferNameRules(null, false).apply("日本語 Customer Name"));
		assertEquals("日本語_CUSTOMER_NAME",
				new TransferNameRules(TransferNameRules.NameCase.UPPER, true).apply("日本語 Customer\u3000Name"));
		assertEquals("customer_name",
				new TransferNameRules(TransferNameRules.NameCase.LOWER, true).apply("CUSTOMER\u00a0NAME"));
		assertEquals("ID", new TransferNameRules(TransferNameRules.NameCase.UPPER, false).apply("id"));
		assertEquals("id", new TransferNameRules(TransferNameRules.NameCase.LOWER, false).apply("ID"));
	}

	@Test
	void usesTheSharedNameContractAndPersistsResolvedOverridesRatherThanRuntimeRules() {
		var rule = new TransferNameRules(TransferNameRules.NameCase.LOWER, true);
		for (String invalid : List.of("", " ", "bad\nname", "\uD800"))
			assertThrows(IllegalArgumentException.class, () -> rule.apply(invalid));
		var io = new TransferNameMappingIO();
		var configuration = new TransferNameMappingIO.Configuration(1,
				Map.of("PUBLIC.CUSTOMERS", new TransferNameMappingIO.TableMapping(rule.apply("CUSTOMER DATA"),
						Map.of("NAME", rule.apply("Customer NAME")))));
		String yaml = io.format(configuration);
		assertEquals(configuration, io.validate(yaml));
		assertEquals("customer_data", io.validate(yaml).tables().get("PUBLIC.CUSTOMERS").targetTable());
		assertFalse(yaml.contains("replaceSpaces"));
		assertFalse(yaml.contains("letterCase"));
	}
}
