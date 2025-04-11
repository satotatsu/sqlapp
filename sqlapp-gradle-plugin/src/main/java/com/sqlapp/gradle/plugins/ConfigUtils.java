/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import com.sqlapp.util.MapUtils;

import groovy.json.JsonSlurper;
import groovy.util.ConfigSlurper;
import groovy.yaml.YamlSlurper;

public class ConfigUtils {

	public static void readConfig(Map<?, ?> binding, @SuppressWarnings("rawtypes") Map map, Collection<File> files) {
		File[] fs = files.stream().toArray(i -> new File[i]);
		readConfig(binding, map, fs);
	}

	@SuppressWarnings("unchecked")
	public static void readConfig(Map<?, ?> binding, @SuppressWarnings("rawtypes") Map map, File... files) {
		final ConfigSlurper slurper = new ConfigSlurper();
		if (binding != null) {
			slurper.setBinding(binding);
		}
		for (final File file : files) {
			if (file.exists() && !file.isDirectory()) {
				final String lowerName = file.getAbsolutePath().toLowerCase();
				if (lowerName.endsWith(".properties")) {
					Properties prop = new Properties();
					try (InputStream is = new FileInputStream(file)) {
						prop.load(is);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					MapUtils.merge(map, (Map<?, ?>) slurper.parse(prop));
				} else if (lowerName.endsWith(".xml")) {
					Properties prop = new Properties();
					try (InputStream is = new FileInputStream(file)) {
						prop.loadFromXML(is);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					MapUtils.merge(map, (Map<?, ?>) slurper.parse(prop));
				} else if (lowerName.endsWith(".json")) {
					JsonSlurper jsonSlurper = new JsonSlurper();
					MapUtils.merge(map, (Map<?, ?>) jsonSlurper.parse(file));
				} else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml")) {
					YamlSlurper yamlSlurper = new YamlSlurper();
					try {
						MapUtils.merge(map, (Map<?, ?>) yamlSlurper.parse(file));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

}
