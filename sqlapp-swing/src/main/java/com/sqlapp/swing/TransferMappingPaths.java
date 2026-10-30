/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.file.Path;

final class TransferMappingPaths {
	static String browseHint(Path job, String reference) {
		if (reference == null || reference.isBlank())
			return job.toString();
		try {
			return job.toAbsolutePath().normalize().getParent().resolve(reference).normalize().toString();
		} catch (java.nio.file.InvalidPathException invalidHint) {
			// Optional chooser navigation only; the unchanged input is still validated on
			// use.
			return reference;
		}
	}

	/** Keep a saved job and its mapping movable together when roots permit it. */
	static String fromJob(Path job, Path mapping) {
		var parent = job.toAbsolutePath().normalize().getParent();
		var target = mapping.toAbsolutePath().normalize();
		if (parent != null && parent.getRoot().equals(target.getRoot())) {
			String relative = parent.relativize(target).toString();
			return java.io.File.separatorChar == '\\' ? relative.replace('\\', '/') : relative;
		}
		return target.toString();
	}
}
