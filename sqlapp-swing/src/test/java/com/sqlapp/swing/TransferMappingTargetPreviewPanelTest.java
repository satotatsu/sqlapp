/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class TransferMappingTargetPreviewPanelTest {
	@Test
	void remainsOptionalAndRequiresExplicitValidVersionWhenSelected() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var changes = new AtomicInteger();
			var panel = new TransferMappingTargetPreviewPanel(changes::incrementAndGet);
			assertNull(panel.dialect());
			panel.product.setSelectedItem("PostgreSQL");
			assertThrows(IllegalArgumentException.class, panel::dialect);
			panel.version.setText("16.0.1");
			assertEquals("PostgreSQL", panel.dialect().getProductName());
			assertTrue(changes.get() > 0);
			panel.version.setText("999999999999999");
			assertThrows(IllegalArgumentException.class, panel::dialect);
			panel.version.setText("16.x");
			assertThrows(IllegalArgumentException.class, panel::dialect);
			panel.product.setSelectedIndex(0);
			assertThrows(IllegalArgumentException.class, panel::dialect);
			panel.version.setText("");
			assertNull(panel.dialect());
		});
	}
}
