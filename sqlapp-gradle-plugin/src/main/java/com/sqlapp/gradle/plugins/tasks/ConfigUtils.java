package com.sqlapp.gradle.plugins.tasks;

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
