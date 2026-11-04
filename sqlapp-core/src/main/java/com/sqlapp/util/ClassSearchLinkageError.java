/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.util;

/** A failed discovery candidate, retaining binary incompatibility and its cause. */
public final class ClassSearchLinkageError extends IncompatibleClassChangeError {
	private static final long serialVersionUID = 1L;
	private final String className;

	public ClassSearchLinkageError(String className, IncompatibleClassChangeError cause) {
		super("Class discovery could not link " + className);
		this.className = java.util.Objects.requireNonNull(className);
		initCause(cause);
	}

	public String getClassName() {
		return className;
	}
}
