/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.commerce.openapi.util.generator;

import com.liferay.commerce.openapi.util.ClassPropertiesFactory;
import com.liferay.commerce.openapi.util.ComponentDefinition;
import com.liferay.commerce.openapi.util.Definition;
import com.liferay.commerce.openapi.util.Path;
import com.liferay.commerce.openapi.util.PropertyDefinition;
import com.liferay.commerce.openapi.util.generator.exception.GeneratorException;
import com.liferay.commerce.openapi.util.importer.OpenAPIImporter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Igor Beslic
 */
public class OSGiRESTModuleGenerator {

	public static void main(String[] args) {
		try {
			OSGiRESTModuleGenerator osgiRESTModuleGenerator =
				new OSGiRESTModuleGenerator();

			osgiRESTModuleGenerator.generate();

			System.exit(0);
		}
		catch (Exception e) {
			_logger.error("Unable to generate module source", e);

			e.printStackTrace();

			System.exit(-1);
		}
	}

	public OSGiRESTModuleGenerator() throws IOException {
		Properties properties = ClassPropertiesFactory.getPropertiesFor(
			getClass());

		_apiPackagePath = properties.getProperty("osgi.module.api.package");
		_applicationBase = properties.getProperty(
			"osgi.module.application.base");
		_applicationClassName = properties.getProperty(
			"osgi.module.application.class.name");
		_applicationName = properties.getProperty(
			"osgi.module.application.name");
		_author = properties.getProperty("osgi.module.author");

		if ("allowed".equals(
				properties.getProperty(
					"osgi.module.application.security.basic"))) {

			_basicSecurityAllowed = true;
		}
		else {
			_basicSecurityAllowed = false;
		}

		_bundleName = properties.getProperty("osgi.module.bundle.name");
		_bundleSynbolicName = properties.getProperty(
			"osgi.module.bundle.symbolic.name");
		_bundleVersion = properties.getProperty("osgi.module.bundle.version");
		_modelPackagePath = properties.getProperty("osgi.module.model.package");

		_moduleOutputPath = String.format(
			"%s/%s", properties.getProperty("osgi.module.root.path"),
			properties.getProperty("osgi.module.name"));

		if ("true".equals(
				properties.getProperty(
					"osgi.module.generator.overwrite.implementation"))) {

			_overwriteImplementation = true;
		}
		else {
			_overwriteImplementation = false;
		}

		if ("true".equals(
				properties.getProperty(
					"osgi.module.generator.overwrite.implementation"))) {

			_overwriteBND = true;
		}
		else {
			_overwriteBND = false;
		}

		_resourceInterfacePackagePath = properties.getProperty(
			"osgi.module.resource.interface.package");
		_resourcePackagePath = properties.getProperty(
			"osgi.module.resource.package");
	}

	public void generate() throws IOException {
		_generateModule();

		_logger.info("Module generated at location {}", _moduleOutputPath);
	}

	private void _checkModuleOutputPath(String moduleOutputPath)
		throws IOException {

		File file = new File(moduleOutputPath);

		if (file.mkdirs()) {
			_logger.info("Created directory {}", file.getAbsolutePath());
		}
	}

	private void _checkModuleOutputPaths() throws IOException {
		_checkModuleOutputPath(_moduleOutputPath + "/");
		_checkModuleOutputPath(_moduleOutputPath + "/src/main/java");
		_checkModuleOutputPath(_moduleOutputPath + "/src/main/resources");
	}

	private boolean _exists(String filePath) {
		File file = new File(filePath);

		return file.exists();
	}

	private void _generateModule() throws IOException {
		OpenAPIImporter openAPIImporter = new OpenAPIImporter();

		Definition definition = openAPIImporter.getDefinition();

		try {
			_checkModuleOutputPaths();

			_writeBNDSource();

			_writeGradleSource();

			Set<String> referencedModels = new HashSet<>();

			for (Path path : definition.getPaths()) {
				referencedModels.addAll(path.getReferencedModels());

				_writeResourceInterfaceSource(definition.getVersion(), path);

				_writeResourceImplementationSource(
					definition.getVersion(), path);
			}

			Set<ComponentDefinition> componentDefinitions =
				definition.getComponentDefinitions();

			for (ComponentDefinition componentDefinition :
					componentDefinitions) {

				if (componentDefinition.isParameter()) {
					continue;
				}

				_writeModelSource(componentDefinition);
			}

			_writeApplicationSource();
		}
		catch (Exception e) {
			throw new GeneratorException("Unable to generate module", e);
		}
	}

	private String _getClassSourcePath(
			String classSourceName, String classPackage)
		throws IOException {

		StringBuilder sb = new StringBuilder();

		sb.append(_moduleOutputPath);
		sb.append("/src/main/java/");
		sb.append(classPackage.replace(".", "/"));
		sb.append("/");

		String packagePath = sb.toString();

		_checkModuleOutputPath(packagePath);

		return packagePath + classSourceName;
	}

	private String _getTemplate(String templateName) throws IOException {
		Class<?> clazz = getClass();

		BufferedReader reader = new BufferedReader(
			new InputStreamReader(clazz.getResourceAsStream(templateName)));

		String line = null;

		StringBuilder stringBuilder = new StringBuilder();

		String ls = System.getProperty("line.separator");

		try {
			while ((line = reader.readLine()) != null) {
				stringBuilder.append(line);
				stringBuilder.append(ls);
			}

			stringBuilder.setLength(stringBuilder.length() - ls.length());

			return stringBuilder.toString();
		}
		finally {
			reader.close();
		}
	}

	private void _writeApplicationSource() throws IOException {
		String osgiApplicationComponent = _getTemplate(
			_TEMPLATE_FILE_APPLICATION);

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${PACKAGE}", _apiPackagePath);

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${AUTHOR}", _author);

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${APPLICATION_BASE}", _applicationBase);

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${APPLICATION_NAME}", _applicationName);

		StringBuilder sb = new StringBuilder();

		if (_basicSecurityAllowed) {
			sb.append("\n\t\t");
			sb.append(_getTemplate("basic.authentication.tpl"));
			sb.append(",");
		}

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${BASIC_AUTHENTICATION}", sb.toString());

		osgiApplicationComponent = osgiApplicationComponent.replace(
			"${APPLICATION_CLASS}", _applicationClassName);

		String componentSourcePath = _getClassSourcePath(
			_applicationClassName + ".java", _apiPackagePath);

		_writeSource(osgiApplicationComponent, componentSourcePath);
	}

	private void _writeBNDSource() throws IOException {
		String bndSourcePath =
			_moduleOutputPath + "/" + _TEMPLATE_FILE_BND.replace(".tpl", "");

		if (!_overwriteBND && _exists(bndSourcePath)) {
			_logger.warn(
				"BND source file {} is not generated. Configure overwrite " +
					"mode in config file.",
				bndSourcePath);

			return;
		}

		String bndTpl = _getTemplate(_TEMPLATE_FILE_BND);

		bndTpl = bndTpl.replace("${BUNDLE_NAME}", _bundleName);
		bndTpl = bndTpl.replace("${BUNDLE_SYMBOLIC_NAME}", _bundleSynbolicName);
		bndTpl = bndTpl.replace("${BUNDLE_VERSION}", _bundleVersion);

		_writeSource(bndTpl, bndSourcePath);
	}

	private void _writeGradleSource() throws IOException {
		String gradleTpl = _getTemplate(_TEMPLATE_FILE_GRADLE);

		_writeSource(
			gradleTpl,
			_moduleOutputPath + "/" +
				_TEMPLATE_FILE_GRADLE.replace(".tpl", ""));
	}

	private void _writeModelSource(ComponentDefinition componentDefinition)
		throws IOException {

		String dtoSource = _getTemplate(_TEMPLATE_FILE_MODEL);

		dtoSource = dtoSource.replace("${PACKAGE}", _modelPackagePath);

		dtoSource = dtoSource.replace("${AUTHOR}", _author);

		String resourceClassName = _baseGenerator.upperCaseFirstChar(
			componentDefinition.getName()) + "DTO";

		dtoSource = dtoSource.replace("${MODEL_CLASS}", resourceClassName);

		List<PropertyDefinition> propertyDefinitions =
			componentDefinition.getPropertyDefinitions();

		Iterator<PropertyDefinition> iterator = propertyDefinitions.iterator();

		StringBuilder methodsSb = new StringBuilder();
		StringBuilder variablesSb = new StringBuilder();

		while (iterator.hasNext()) {
			PropertyDefinition propertyDefinition = iterator.next();

			String name = propertyDefinition.getName();

			methodsSb.append("\tpublic ");
			methodsSb.append(propertyDefinition.getJavaType());
			methodsSb.append(" get");
			methodsSb.append(_baseGenerator.upperCaseFirstChar(name));
			methodsSb.append("() {\n\t\treturn _");
			methodsSb.append(name);
			methodsSb.append(";\n\t}\n\n");

			methodsSb.append("\tpublic void set");
			methodsSb.append(_baseGenerator.upperCaseFirstChar(name));
			methodsSb.append("(");
			methodsSb.append(propertyDefinition.getJavaType());
			methodsSb.append(" ");
			methodsSb.append(name);
			methodsSb.append(") {\n\t\t_");
			methodsSb.append(name);
			methodsSb.append(" = ");
			methodsSb.append(name);
			methodsSb.append(";\n\t}");

			if (iterator.hasNext()) {
				methodsSb.append("\n\n");
			}

			variablesSb.append("\tprivate ");
			variablesSb.append(propertyDefinition.getJavaType());
			variablesSb.append(" _");
			variablesSb.append(name);
			variablesSb.append(";");

			if (iterator.hasNext()) {
				variablesSb.append("\n");
			}
		}

		dtoSource = dtoSource.replace("${METHODS}", methodsSb.toString());
		dtoSource = dtoSource.replace("${VARIABLES}", variablesSb.toString());

		String componentSourcePath = _getClassSourcePath(
			resourceClassName + ".java", _modelPackagePath);

		_writeSource(dtoSource, componentSourcePath);
	}

	private void _writeResourceImplementationSource(String version, Path path)
		throws IOException {

		String resourceImplementationClassName =
			_baseGenerator.upperCaseFirstChar(path.getName() + "ResourceImpl");

		String componentSourcePath = _getClassSourcePath(
			resourceImplementationClassName + ".java", _resourcePackagePath);

		if (!_overwriteImplementation && _exists(componentSourcePath)) {
			_logger.warn(
				"Resource implementation source file {} is not generated. " +
					"Configure overwrite mode in config file.",
				componentSourcePath);

			return;
		}

		String osgiResourceComponent = _getTemplate(
			_TEMPLATE_FILE_RESOURCE_IMPLEMENTATION);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${PACKAGE}", _resourcePackagePath);

		StringBuilder sb = new StringBuilder();

		sb.append(
			_resourceGenerator.toModelImportStatements(
				_modelPackagePath, path.getReferencedModels()));

		sb.append("import ");
		sb.append(_resourceInterfacePackagePath);
		sb.append(".");
		sb.append(_baseGenerator.upperCaseFirstChar(path.getName()));
		sb.append("Resource;");

		osgiResourceComponent = osgiResourceComponent.replace(
			"${IMPORT_STATEMENTS}", sb.toString());

		osgiResourceComponent = osgiResourceComponent.replace(
			"${API_VERSION}", version);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${AUTHOR}", _author);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${APPLICATION_NAME}", _applicationName);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_IMPORT_STATEMENTS_JAVAX}",
			_resourceGenerator.toJavaxImports(path.getMethods()));

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_RESOURCE_IMPLEMENTATION_CLASS}",
			resourceImplementationClassName);

		String resourceInterfaceClassName = _baseGenerator.upperCaseFirstChar(
			path.getName() + "Resource");

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_RESOURCE_INTERFACE_CLASS}", resourceInterfaceClassName);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${PATH}", path.getName());

		osgiResourceComponent = osgiResourceComponent.replace(
			"${METHODS}",
			_resourceGenerator.toResourceImplementationMethods(
				path.getMethods()));

		_writeSource(osgiResourceComponent, componentSourcePath);
	}

	private void _writeResourceInterfaceSource(String version, Path path)
		throws IOException {

		String osgiResourceComponent = _getTemplate(
			_TEMPLATE_FILE_RESOURCE_INTERFACE);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${PACKAGE}", _resourceInterfacePackagePath);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_IMPORT_STATEMENTS}",
			_resourceGenerator.toModelImportStatements(
				_modelPackagePath, path.getReferencedModels()));

		osgiResourceComponent = osgiResourceComponent.replace(
			"${API_VERSION}", version);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${AUTHOR}", _author);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_IMPORT_STATEMENTS_JAVAX}",
			_resourceGenerator.toJavaxImports(path.getMethods()));

		osgiResourceComponent = osgiResourceComponent.replace(
			"${PATH}", path.getName());

		String resourceInterfaceClassName = _baseGenerator.upperCaseFirstChar(
			path.getName() + "Resource");

		osgiResourceComponent = osgiResourceComponent.replace(
			"${MODEL_RESOURCE_INTERFACE_CLASS}", resourceInterfaceClassName);

		osgiResourceComponent = osgiResourceComponent.replace(
			"${METHODS}",
			_resourceGenerator.toResourceInterfaceMethods(path.getMethods()));

		String componentSourcePath = _getClassSourcePath(
			resourceInterfaceClassName + ".java",
			_resourceInterfacePackagePath);

		_writeSource(osgiResourceComponent, componentSourcePath);
	}

	private void _writeSource(String content, String fileName)
		throws IOException {

		File file = new File(fileName);

		BufferedWriter bufferedWriter = new BufferedWriter(
			new FileWriter(file));

		try {
			bufferedWriter.write(content.toCharArray());
		}
		finally {
			bufferedWriter.flush();

			bufferedWriter.close();
		}
	}

	private static final String _TEMPLATE_FILE_APPLICATION =
		"Application.java.tpl";

	private static final String _TEMPLATE_FILE_BND = "bnd.bnd.tpl";

	private static final String _TEMPLATE_FILE_GRADLE = "build.gradle.tpl";

	private static final String _TEMPLATE_FILE_MODEL = "Model.java.tpl";

	private static final String _TEMPLATE_FILE_RESOURCE_IMPLEMENTATION =
		"ResourceImpl.java.tpl";

	private static final String _TEMPLATE_FILE_RESOURCE_INTERFACE =
		"Resource.java.tpl";

	private static final Logger _logger = LoggerFactory.getLogger(
		OSGiRESTModuleGenerator.class);

	private final String _apiPackagePath;
	private final String _applicationBase;
	private final String _applicationClassName;
	private final String _applicationName;
	private final String _author;
	private final BaseGenerator _baseGenerator = new BaseGenerator();
	private final boolean _basicSecurityAllowed;
	private final String _bundleName;
	private final String _bundleSynbolicName;
	private final String _bundleVersion;
	private final String _modelPackagePath;
	private final String _moduleOutputPath;
	private final boolean _overwriteBND;
	private final boolean _overwriteImplementation;
	private final ResourceGenerator _resourceGenerator =
		new ResourceGenerator();
	private final String _resourceInterfacePackagePath;
	private final String _resourcePackagePath;

}