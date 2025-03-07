/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module Helper
 */
public class ModuleHelper {

	private static ModuleHelper instance = new ModuleHelper();

	private Map<String, Module> packageModuleCache = CommonUtils.concurrentMap();
	private Map<String, ModuleReference> packageModuleReferenceCache = CommonUtils.concurrentMap();
	private Map<String, ModuleReference> moduleReferenceCache = CommonUtils.concurrentMap();
	private Map<String, Module> moduleCache = CommonUtils.concurrentMap();
	private Map<String, Optional<URI>> packageURLCache = CommonUtils.concurrentMap();

	/*
	 * インスタンスを取得します
	 * 
	 * @return インスタンス
	 */
	public static ModuleHelper getInstance() {
		return instance;
	}

	private ModuleHelper() {
		String modulePath = System.getProperty("jdk.module.path");
		if (CommonUtils.isEmpty(modulePath)) {
			return;
		}
		String[] args = modulePath.split(";");
		List<Path> paths = Arrays.stream(args).map(a -> Path.of(a)).collect(Collectors.toList());
		ModuleFinder finder = ModuleFinder.of(paths.toArray(new Path[0]));
		Set<ModuleReference> moduleRefs = finder.findAll();
		ModuleLayer parent = ModuleLayer.boot();
		ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
		for (ModuleReference mfref : moduleRefs) {
			Optional<Module> opModule = parent.findModule(mfref.descriptor().name());
			Module module;
			if (opModule.isPresent()) {
				module = opModule.get();
			} else {
				Configuration configuration = parent.configuration().resolve(finder, ModuleFinder.of(),
						Set.of(mfref.descriptor().name()));
				ModuleLayer layer = parent.defineModulesWithOneLoader(configuration, systemClassLoader);
				module = layer.findModule(mfref.descriptor().name()).orElse(null);
			}
			moduleReferenceCache.putIfAbsent(module.getName(), mfref);
			moduleCache.putIfAbsent(module.getName(), module);
			module.getPackages().forEach(p -> {
				packageURLCache.put(p, mfref.location());
				packageModuleCache.putIfAbsent(p, module);
				packageModuleReferenceCache.putIfAbsent(p, mfref);
			});
		}
	}

	/**
	 * パッケージ名に対応したモジュールを取得します
	 * 
	 * @param packageName パッケージ名
	 * @return パッケージ名に対応したモジュール
	 */
	public Module getModuleByPackage(String packageName) {
		return packageModuleCache.get(packageName);
	}

	/**
	 * パッケージ名に前方一致するモジュールリファレンスを取得します
	 * 
	 * @param packageName パッケージ名
	 * @return パッケージ名に前方一致するモジュールリファレンス
	 */
	public Set<ModuleReference> findModuleReferencesByPackage(String packageName) {
		return packageModuleReferenceCache.entrySet().stream().filter(e -> e.getKey().startsWith(packageName))
				.map(e -> e.getValue()).collect(Collectors.toSet());
	}

	/**
	 * パッケージ名に対応したモジュールリファレンスを取得します
	 * 
	 * @param packageName パッケージ名
	 * @return パッケージ名に対応したモジュールリファレンス
	 */
	public ModuleReference getModuleReferenceByPackage(String packageName) {
		return packageModuleReferenceCache.get(packageName);
	}

	/**
	 * 指定したクラスをモジュールから取得します
	 * 
	 * @param className クラス名
	 * @return クラス
	 */
	@SuppressWarnings("unchecked")
	public <X> Class<X> getClass(String className) {
		int pos = className.lastIndexOf(".");
		String packageName;
		if (pos >= 0) {
			packageName = className.substring(0, pos);
			Module module = getModuleByPackage(packageName);
			return (Class<X>) Class.forName(module, className);
		}
		return null;
	}

	/**
	 * パッケージ名に対応したURIを取得します
	 * 
	 * @param packageName パッケージ名
	 * @return パッケージ名に対応したURI
	 */
	public Optional<URI> getURIByPackage(String packageName) {
		return packageURLCache.get(packageName);
	}

	/**
	 * パッケージのセットを返します
	 * 
	 * @return パッケージのセット
	 */
	public Set<String> getPackages() {
		return packageModuleCache.keySet();
	}

	/**
	 * 指定したリソースのInputStreamを取得します
	 * 
	 * @param resource リソースパス
	 * @return リソースのInputStream
	 * @throws IOException
	 */
	public InputStream getResourceAsStream(String resource) throws IOException {
		int pos = resource.lastIndexOf("/");
		String pack = resource.substring(0, pos).replace("/", ".");
		Module module = packageModuleCache.get(pack);
		if (module != null) {
			return module.getResourceAsStream(resource);
		}
		return null;
	}

	/**
	 * 指定したモジュール、リソースのInputStreamを取得します
	 * 
	 * @param module   モジュール
	 * @param resource リソースパス
	 * @return リソースのInputStream
	 * @throws IOException
	 */
	public InputStream getResourceAsStream(Module module, String resource) throws IOException {
		resource = resource.replace("/", ".");
		return module.getResourceAsStream(resource);
	}

	/**
	 * モジュールからクラスを取得します。
	 * 
	 * @param <T>       クラスの型
	 * @param className クラス名
	 * @return クラス
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public <T> Class<T> forName(String className) throws ClassNotFoundException {
		int pos = className.lastIndexOf(".");
		if (pos >= 0) {
			String pack = className.substring(0, pos);
			Module module = packageModuleCache.get(pack);
			if (module != null) {
				return (Class<T>) Class.forName(module, className);
			}
		}
		return (Class<T>) Class.forName(className);
	}
}
