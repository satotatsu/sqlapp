/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferJobConfiguration.*;

class FileTransferOptionsPanelTest {
	@Test
	void validatesEditableEncodingKeepsSafeDefaultsAndDisablesJsonlHeadersAndBusyInputs() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var options = new FileTransferOptionsPanel();
			var defaults = options.settings("packages", FileFormat.CSV, "__NULL__", TemporalPolicy.WARN);
			assertEquals("UTF-8", defaults.encoding());
			assertEquals(HeaderMode.VALIDATE, defaults.headerMode());
			options.encoding.getEditor().setItem("MS932");
			options.headerMode.setSelectedItem(HeaderMode.SKIP);
			var custom = options.settings("packages", FileFormat.TSV, "__NULL__", TemporalPolicy.ERROR);
			assertEquals("windows-31j", custom.encoding());
			assertEquals(HeaderMode.SKIP, custom.headerMode());
			assertEquals(TemporalPolicy.ERROR, custom.unsupportedTemporalPolicy());
			options.refreshEnabled(true, FileFormat.JSONL);
			assertFalse(options.headerMode.isEnabled());
			assertEquals(HeaderMode.NONE,
					options.settings("packages", FileFormat.JSONL, null, TemporalPolicy.WARN).headerMode());
			options.encoding.getEditor().setItem("not-an-encoding");
			assertTrue(assertThrows(IllegalArgumentException.class,
					() -> options.settings("packages", FileFormat.CSV, "__NULL__", TemporalPolicy.WARN)).getMessage()
					.contains("character encoding"));
			options.load(new FileTransfer("packages", FileFormat.CSV, "UTF-16LE", "__NULL__", TemporalPolicy.WARN,
					HeaderMode.NONE));
			assertEquals("UTF-16LE", options.encoding.getSelectedItem());
			assertEquals(HeaderMode.NONE, options.headerMode.getSelectedItem());
			options.refreshEnabled(false, FileFormat.CSV);
			assertFalse(options.encoding.isEnabled());
			assertFalse(options.headerMode.isEnabled());
		});
	}
}
