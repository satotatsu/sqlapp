/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ComponentEnablementTest {
	@Test
	void walksNestedContainersButLeavesSelfManagedChildrenToTheirOwner() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var form = new JPanel();
			final var nested = new JPanel();
			final var field = new JTextField();
			nested.add(field);
			form.add(nested);
			final var ownAction = new JButton("own");
			final class Owner extends JPanel implements ComponentEnablement.SelfManaged {
				private static final long serialVersionUID = 1L;
				boolean busy = true;

				@Override
				public void setEnabled(final boolean enabled) {
					super.setEnabled(enabled);
					ownAction.setEnabled(enabled && !busy);
				}
			}
			final var owner = new Owner();
			owner.add(ownAction);
			form.add(owner);

			ComponentEnablement.setTreeEnabled(form, false);
			assertFalse(nested.isEnabled());
			assertFalse(field.isEnabled());
			assertFalse(owner.isEnabled());
			assertFalse(ownAction.isEnabled());

			ComponentEnablement.setTreeEnabled(form, true);
			assertTrue(field.isEnabled());
			assertTrue(owner.isEnabled());
			assertFalse(ownAction.isEnabled(), "busy owner keeps its action disabled");
		});
	}

	@Test
	void connectionProfileKeepsItsActionsConsistentWithItsEnabledState() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var profile = new ConnectionProfilePanel(new JTextField(), new JTextField(), new JTextField(),
					new JPasswordField());
			final var form = new JPanel();
			form.add(profile);
			ComponentEnablement.setTreeEnabled(form, false);
			assertFalse(profile.load.isEnabled());
			assertFalse(profile.save.isEnabled());
			ComponentEnablement.setTreeEnabled(form, true);
			assertTrue(profile.load.isEnabled());
			assertTrue(profile.save.isEnabled());
		});
	}
}
