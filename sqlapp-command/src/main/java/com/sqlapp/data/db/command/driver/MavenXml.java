/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.driver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Small, entity-safe DOM helpers for POM and metadata files. */
final class MavenXml {
	private MavenXml() {
	}

	static Element parse(final byte[] content, final String description) throws IOException {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final var builder = factory.newDocumentBuilder();
			builder.setErrorHandler(null);
			return builder.parse(new ByteArrayInputStream(content)).getDocumentElement();
		} catch (final ParserConfigurationException | SAXException failure) {
			throw new IOException("Unable to read " + description + ": " + failure.getMessage(), failure);
		}
	}

	static Element child(final Element parent, final String name) {
		if (parent == null)
			return null;
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
			if (node instanceof Element element && name.equals(localName(element)))
				return element;
		return null;
	}

	static List<Element> children(final Element parent, final String name) {
		final var result = new ArrayList<Element>();
		if (parent != null)
			for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
				if (node instanceof Element element && (name == null || name.equals(localName(element))))
					result.add(element);
		return result;
	}

	static String text(final Element parent, final String name) {
		final Element element = child(parent, name);
		if (element == null)
			return null;
		final String value = element.getTextContent().trim();
		return value.isEmpty() ? null : value;
	}

	static String localName(final Element element) {
		final String name = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
		final int colon = name.indexOf(':');
		return colon < 0 ? name : name.substring(colon + 1);
	}
}
