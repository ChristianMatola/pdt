/*******************************************************************************
 * Copyright (c) 2006 Zend Corporation and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zend and IBM - Initial implementation
 *******************************************************************************/
package org.eclipse.php.internal.core.phpModel.parser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.util.NLS;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.phpModel.IPHPModelExtension;
import org.eclipse.php.internal.core.phpModel.IPHPLanguageModel;
import org.eclipse.php.internal.core.phpModel.phpElementData.CodeData;
import org.eclipse.php.internal.core.phpModel.phpElementData.IPHPMarker;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPClassConstData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPClassData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPClassVarData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPConstantData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPDocBlock;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPDocTag;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFileData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFunctionData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPVariableData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFunctionData.PHPFunctionParameter;

public abstract class PHPLanguageModel implements IPHPLanguageModel {

	protected PHPFunctionData[] functions = PHPCodeDataFactory.EMPTY_FUNCTIONS_DATA_ARRAY;
	protected Map<String, PHPFunctionData> functionsHash = new HashMap<String, PHPFunctionData>(3000);
	protected PHPClassData[] classes = PHPCodeDataFactory.EMPTY_CLASS_DATA_ARRAY;
	protected Map<String, PHPClassData> classesHash = new HashMap<String, PHPClassData>(10);
	protected PHPConstantData[] constants = PHPCodeDataFactory.EMPTY_CONSTANT_DATA_ARRAY;
	protected IPHPMarker[] markers = PHPCodeDataFactory.EMPTY_MARKERS_DATA_ARRAY;
	protected PHPVariableData[] phpVariables;
	protected PHPVariableData[] serverVariables;
	protected PHPVariableData[] sessionVariables;
	protected PHPVariableData[] classVariables;

	public PHPLanguageModel(PHPLanguageManager languageManager) {
		loadFiles(languageManager);
		initVariables();
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public PHPVariableData[] getPHPVariables() {
		return phpVariables;
	}

	public PHPVariableData[] getServerVariables() {
		return serverVariables;
	}

	public PHPVariableData[] getSessionVariables() {
		return sessionVariables;
	}

	public PHPVariableData[] getClassVariables() {
		return classVariables;
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public CodeData[] getFunctions() {
		return functions;
	}

	public CodeData[] getFunction(String functionName) {
		PHPFunctionData function = getFunction(null, functionName);
		if (function == null) {
			return PHPCodeDataFactory.EMPTY_CODE_DATA_ARRAY;
		}
		return new PHPFunctionData[] { function };
	}

	public PHPFunctionData getFunction(String fileName, String functionName) {
		return functionsHash.get(functionName);
	}

	public CodeData[] getFunctions(String startsWith) {
		return ModelSupport.getCodeDataStartingWith(getFunctions(), startsWith);
	}

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public CodeData[] getClasses() {
		return classes;
	}

	public CodeData[] getClasses(String startsWith) {
		return ModelSupport.getCodeDataStartingWith(classes, startsWith);
	}

	public CodeData[] getConstants() {
		return constants;
	}

	public CodeData[] getConstants(String startsWith, boolean caseSensitive) {
		return caseSensitive ? ModelSupport.getCodeDataStartingWithCS(getConstants(), startsWith) : ModelSupport.getCodeDataStartingWith(getConstants(), startsWith);
	}

	public PHPConstantData getConstant(String fileName, String constantName) {
		return null;
	}

	// ////////////////////////////////////////////////////////////////////////////////////////

	public CodeData[] getFileDatas() {
		return null;
	}

	public CodeData[] getNonPHPFiles(String startsWith) {
		return null;
	}

	public PHPFileData getFileData(String fileName) {
		return null;
	}

	public PHPClassData getClass(String fileName, String className) {
		return classesHash.get(getNormalizedString(className));
	}

	public CodeData[] getClass(String className) {
		PHPClassData classs = getClass(null, className);
		if (classs == null) {
			return PHPCodeDataFactory.EMPTY_CODE_DATA_ARRAY;
		}
		return new CodeData[] { classs };
	}

	public CodeData[] getGlobalVariables(String fileName, String startsWith, boolean showVariablesFromOtherFiles) {
		return phpVariables;
	}

	public CodeData[] getVariables(String fileName, PHPCodeContext context, String startsWith, boolean showVariablesFromOtherFiles) {
		String className = context.getContainerClassName();
		if (className == null || className.equals("")) { //$NON-NLS-1$
			return phpVariables;
		}
		return ModelSupport.merge(phpVariables, classVariables);
	}

	public String getVariableType(String fileName, PHPCodeContext context, String variableName, int line, boolean showObjectsFromOtherFiles) {
		return null;
	}

	public CodeData[] getConstant(String constantName) {
		return null;
	}

	public IPHPMarker[] getMarkers() {
		return markers;
	}

	public void clear() {
	}

	public void dispose() {
	}

	public void initialize(IProject project) {
	}

	//////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Returns a trimmed string changed to lower case
	 */
	private final String getNormalizedString(final String name) {
		return (name == null) ? null : name.trim().toLowerCase();
	}

	private void initVariables() {

		phpVariables = new PHPVariableData[] { PHPCodeDataFactory.createPHPVariableData("_GET", null, null), PHPCodeDataFactory.createPHPVariableData("_POST", null, null), PHPCodeDataFactory.createPHPVariableData("_COOKIE", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			PHPCodeDataFactory.createPHPVariableData("_SESSION", null, null), PHPCodeDataFactory.createPHPVariableData("_SERVER", null, null), PHPCodeDataFactory.createPHPVariableData("_ENV", null, null), PHPCodeDataFactory.createPHPVariableData("_REQUEST", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("_FILES", null, null), PHPCodeDataFactory.createPHPVariableData("GLOBALS", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_GET_VARS", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_POST_VARS", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("HTTP_COOKIE_VARS", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_SESSION_VARS", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_SERVER_VARS", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			PHPCodeDataFactory.createPHPVariableData("HTTP_ENV_VARS", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_POST_FILES", null, null) }; //$NON-NLS-1$ //$NON-NLS-2$
		Arrays.sort(phpVariables);

		serverVariables = new PHPVariableData[] { PHPCodeDataFactory.createPHPVariableData("DOCUMENT_ROOT", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_ACCEPT", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_ACCEPT_ENCODING", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			PHPCodeDataFactory.createPHPVariableData("HTTP_ACCEPT_LANGUAGE", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_CONNECTION", null, null), PHPCodeDataFactory.createPHPVariableData("HTTP_HOST", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			PHPCodeDataFactory.createPHPVariableData("HTTP_USER_AGENT", null, null), PHPCodeDataFactory.createPHPVariableData("REMOTE_ADDR", null, null), PHPCodeDataFactory.createPHPVariableData("SCRIPT_FILENAME", null, null), PHPCodeDataFactory.createPHPVariableData("SERVER_NAME", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("GATEWAY_INTERFACE", null, null), PHPCodeDataFactory.createPHPVariableData("REQUEST_METHOD", null, null), PHPCodeDataFactory.createPHPVariableData("QUERY_STRING", null, null), PHPCodeDataFactory.createPHPVariableData("REQUEST_URI", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("SCRIPT_NAME", null, null), PHPCodeDataFactory.createPHPVariableData("PHP_SELF", null, null), PHPCodeDataFactory.createPHPVariableData("PATH", null, null), PHPCodeDataFactory.createPHPVariableData("REMOTE_PORT", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("SERVER_ADDR", null, null), PHPCodeDataFactory.createPHPVariableData("SERVER_ADMIN", null, null), PHPCodeDataFactory.createPHPVariableData("SERVER_PORT", null, null), PHPCodeDataFactory.createPHPVariableData("SERVER_SIGNATURE", null, null), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			PHPCodeDataFactory.createPHPVariableData("SERVER_SOFTWARE", null, null), PHPCodeDataFactory.createPHPVariableData("SERVER_PROTOCOL", null, null), PHPCodeDataFactory.createPHPVariableData("PATH_TRANSLATED", null, null), }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Arrays.sort(serverVariables);

		classVariables = new PHPVariableData[] { PHPCodeDataFactory.createPHPVariableData("this", null, null) }; //$NON-NLS-1$
		Arrays.sort(classVariables);

		sessionVariables = new PHPVariableData[] { PHPCodeDataFactory.createPHPVariableData("SID", null, null) }; //$NON-NLS-1$

	}

	// ////////////////////////////////////////////////////////////////////////////////////////

	// ////////////////////////////////////////////////////////////////////////////////////////

	protected void loadFiles(PHPLanguageManager languageManager) {
		try {
			final PHPParserManager phpParserManager = languageManager.createPHPParserManager();

			// parse the specific language model
			String phpFunctionPath = languageManager.getPHPFunctionPath();
			Reader reader = new InputStreamReader(FileLocator.openStream(PHPCorePlugin.getDefault().getBundle(), new Path(phpFunctionPath), false));
			ParserClient innerParserClient = new InnerParserClient();

			ParserExecuter executer = new ParserExecuter(phpParserManager, null, innerParserClient, phpFunctionPath, reader, new Pattern[0], 0, false);
			executer.run();

			// load language model extensions:
			IExtensionRegistry registry = Platform.getExtensionRegistry();
			IConfigurationElement[] elements = registry.getConfigurationElementsFor(PHPCorePlugin.ID, "phpModelExtensions"); //$NON-NLS-1$

			for (IConfigurationElement element : elements) {
				if ("model".equals(element.getName())) { //$NON-NLS-1$
					String id = element.getAttribute("id"); //$NON-NLS-1$

					try {
						String enabledAttr = element.getAttribute("enabled"); //$NON-NLS-1$
						boolean enabled = (enabledAttr == null) ? true : Boolean.parseBoolean(enabledAttr);
						String file = element.getAttribute("file"); //$NON-NLS-1$
						String phpVersion = element.getAttribute("phpVersion"); //$NON-NLS-1$

						if (element.getAttribute("class") != null) { //$NON-NLS-1$
							IPHPModelExtension extension = (IPHPModelExtension) element.createExecutableExtension("class"); //$NON-NLS-1$
							enabled = extension.isEnabled();
							if (extension.getFile() != null) {
								file = extension.getFile();
							}
							if (extension.getPHPVersion() != null) {
								phpVersion = extension.getPHPVersion();
							}
						}

						if (enabled && getPHPVersion().equals(phpVersion)) {
							reader = new InputStreamReader(FileLocator.openStream(Platform.getBundle(element.getNamespaceIdentifier()), new Path(file), false));
							innerParserClient = new InnerParserClient();
							executer = new ParserExecuter(phpParserManager, null, innerParserClient, file, reader, new Pattern[0], 0, false);
							executer.run();
						}
					} catch (CoreException e) {
						PHPCorePlugin.logErrorMessage(NLS.bind("Error loading PHP model extension ID {0}", id)); //$NON-NLS-1$
					}
				}
			}

			Arrays.sort(functions);
			Arrays.sort(classes);
			Arrays.sort(constants);

		} catch (IOException e) {
			PHPCorePlugin.log(e);
		}
	}

	protected class InnerParserClient implements ParserClient {

		private String className = ""; //$NON-NLS-1$
		private List<PHPFunctionData> functionsList = new ArrayList<PHPFunctionData>(3000);
		private List<PHPClassConstData> classConstsList = new ArrayList<PHPClassConstData>();
		private List<PHPClassVarData> classVarsList = new ArrayList<PHPClassVarData>();
		private List<PHPFunctionData> classFunctionsList = new ArrayList<PHPFunctionData>();
		private List<PHPClassData> classesList = new ArrayList<PHPClassData>();
		private List<PHPConstantData> constansList = new ArrayList<PHPConstantData>(2000);
		private List<PHPFunctionParameter> functionParametersList = new ArrayList<PHPFunctionParameter>();

		public void dispose() {
			functionsList.clear();
			classConstsList.clear();
			classVarsList.clear();
			classFunctionsList.clear();
			classesList.clear();
			constansList.clear();
			functionParametersList.clear();
		}

		public void handleFunctionParameter(String classType, String variableName, boolean isReference, boolean isConst, String defaultValue, int startPosition, int endPosition, int stopPosition, int lineNumber) {
			variableName = variableName.substring(1);
			functionParametersList.add(PHPCodeDataFactory.createPHPFunctionParameter(variableName, null, isReference, isConst, classType, defaultValue));
		}

		public void hadleClassDeclarationStarts(String className, int startPosition) {
			this.className = className;
		}

		private PHPFunctionData.PHPFunctionParameter getParameter(PHPFunctionData.PHPFunctionParameter[] parameters, String parameterName) {
			if (parameterName == null || parameterName.length() == 0) {
				return null;
			}
			if (parameterName.charAt(0) == '&') {
				parameterName = parameterName.substring(1);
			}
			if (parameterName.charAt(0) == '$') {
				parameterName = parameterName.substring(1);
			}
			for (PHPFunctionParameter element : parameters) {
				if (parameterName.equalsIgnoreCase(element.getName())) {
					return element;
				}
			}
			return null;
		}

		public void handleFunctionDeclarationStarts(String functionName) {
		}

		public void handleFunctionDeclaration(String functionName, boolean isClassFunction, int modifier, PHPDocBlock docInfo, int startPosition, int stopPosition, int lineNumber) {
			PHPFunctionData.PHPFunctionParameter[] parameters = new PHPFunctionData.PHPFunctionParameter[functionParametersList.size()];
			functionParametersList.toArray(parameters);
			functionParametersList.clear();

			String returnType = null;

			if (docInfo != null) {
				Iterator<PHPDocTag> it = docInfo.getTags(PHPDocTag.PARAM);
				while (it.hasNext()) {
					PHPDocTag param = it.next();
					String arg = param.getValue().trim();
					String[] values = arg.split(" "); //$NON-NLS-1$
					String name = null;
					String type = null;

					int length = values.length > 2 ? 2 : values.length;
					for (int i = 0; i < length; i++) {
						if (values[i].startsWith("$")) { //$NON-NLS-1$
							name = values[i];
						} else {
							type = values[i];
						}
					}
					if (name == null) {
						name = values[0];
					}
					PHPFunctionData.PHPFunctionParameter parameter = getParameter(parameters, name);

					if (parameter == null) {
						type = values[0];
						name = values.length > 1 ? values[1] : null;
						parameter = getParameter(parameters, type);
					}
					// update parameter.
					if (parameter != null && type != null && type.length() > 0) {
						type = getType(type);
						String originalClassType = parameter.getClassType();
						if (originalClassType == null || originalClassType.length() == 0) {
							parameter.setClassType(type);
						}
					}
				}
				Iterator<PHPDocTag> returnIt = docInfo.getTags(PHPDocTag.RETURN);
				returnType = returnIt.hasNext() ? (String) returnIt.next().getValue() : null;
			}

			if (returnType == null) {
				if (isClassFunction && functionName.equals(className)) {
					returnType = className;
				} else {
					returnType = TYPE_VOID; //$NON-NLS-1$
				}
			} else {
				returnType = getType(returnType);
			}

			PHPFunctionData functionData = PHPCodeDataFactory.createPHPFuctionData(functionName, modifier, docInfo, null, parameters, returnType);
			if (isClassFunction) {
				classFunctionsList.add(functionData);
			} else {
				functionsList.add(functionData);
			}
		}

		/**
		 * lowering memory footprint by using static objects
		 */
		private String getType(String type) {
			if(TYPE_STRING.equals(type)){
				return TYPE_STRING;
			}
			if(TYPE_INT.equals(type)){
				return TYPE_INT;
			}
			if(TYPE_BOOL.equals(type)){
				return TYPE_BOOL;
			}
			if(TYPE_RESOURCE.equals(type)){
				return TYPE_RESOURCE;
			}
			if(TYPE_ARRAY.equals(type)){
				return TYPE_ARRAY;
			}
			if(TYPE_MIXED.equals(type)){
				return TYPE_MIXED;
			}
			if(TYPE_OBJECT.equals(type)){
				return TYPE_OBJECT;
			}
			if(TYPE_VOID.equals(type)){
				return TYPE_VOID;
			}
			return type;
		}

		public void handleFunctionDeclarationEnds(boolean isClassFunction, int endPosition) {
		}

		public void handleClassDeclaration(String className, int modifier, String superClassName, String interfacesNames, PHPDocBlock docInfo, int startPosition, int stopPosition, int lineNumber) {
			PHPClassData.PHPSuperClassNameData superClassData;
			if (superClassName != null) {
				int index = superClassName.indexOf(']');
				superClassName = superClassName.substring(index + 1);
				superClassData = PHPCodeDataFactory.createPHPSuperClassNameData(superClassName, null);
			} else {
				superClassData = PHPCodeDataFactory.createPHPSuperClassNameData(superClassName, null);
			}

			PHPClassData.PHPInterfaceNameData[] interfaces;
			if (interfacesNames != null) {
				String[] interfacesNamesArray = interfacesNames.split(","); //$NON-NLS-1$
				interfaces = new PHPClassData.PHPInterfaceNameData[interfacesNamesArray.length];
				for (int i = 0; i < interfacesNamesArray.length; i++) {
					String interfaceName = interfacesNamesArray[i];
					int index = interfaceName.indexOf(']');
					interfaceName = interfaceName.substring(index + 1);
					interfaces[i] = PHPCodeDataFactory.createPHPInterfaceNameData(interfaceName, null);
				}
			} else {
				interfaces = PHPCodeDataFactory.EMPTY_INTERFACES_DATA_ARRAY;
			}

			PHPClassData classData = PHPCodeDataFactory.createPHPClassData(className, modifier, docInfo, null, superClassData, interfaces, PHPCodeDataFactory.EMPTY_CLASS_VAR_DATA_ARRAY, PHPCodeDataFactory.EMPTY_CLASS_CONST_DATA_ARRAY, PHPCodeDataFactory.EMPTY_FUNCTIONS_DATA_ARRAY);

			classesList.add(classData);
		}

		public void handleClassDeclarationEnds(String className, int endPosition) {
			if (classesList.size() > 0) {
				PHPCodeDataFactory.PHPClassDataImp classData = (PHPCodeDataFactory.PHPClassDataImp) classesList.get(classesList.size() - 1);
				if (classData.getName().equals(className)) {
					PHPClassConstData[] consts = new PHPClassConstData[classConstsList.size()];
					classConstsList.toArray(consts);
					Arrays.sort(consts);

					PHPClassVarData[] vars = new PHPClassVarData[classVarsList.size()];
					classVarsList.toArray(vars);
					Arrays.sort(vars);

					PHPFunctionData[] func = new PHPFunctionData[classFunctionsList.size()];
					classFunctionsList.toArray(func);
					Arrays.sort(func);

					for (int i = 0; i < consts.length; ++i) {
						((PHPCodeDataFactory.PHPClassConstDataImp) consts[i]).setContainer(classData);
					}
					for (PHPClassVarData element : vars) {
						((PHPCodeDataFactory.PHPClassVarDataImp) element).setContainer(classData);
					}
					for (PHPFunctionData element : func) {
						((PHPCodeDataFactory.PHPFunctionDataImp) element).setContainer(classData);
					}

					classData.setConsts(consts);
					classData.setFunctions(func);
					classData.setVars(vars);
				}
			}

			classConstsList.clear();
			classVarsList.clear();
			classFunctionsList.clear();

			this.className = ""; //$NON-NLS-1$
		}

		public void handleClassVariablesDeclaration(String variables, int modifier, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
			String classType = null;
			if (docInfo != null) {
				Iterator<PHPDocTag> it = docInfo.getTags(PHPDocTag.VAR);
				while (it.hasNext()) {
					PHPDocTag varTag = it.next();
					String value = varTag.getValue().trim();
					String[] values = value.split(" "); //$NON-NLS-1$
					classType = values[0];
				}
			}
			StringTokenizer t = new StringTokenizer(variables, ",", false); //$NON-NLS-1$
			while (t.hasMoreTokens()) {
				String var = t.nextToken().substring(1);
				PHPClassVarData classVarData = PHPCodeDataFactory.createPHPClassVarData(var, modifier, classType, docInfo, null);
				handleObjectInstansiation(var, classType, null, 0, 0, false);
				classVarsList.add(classVarData);
			}
		}

		public void handleClassConstDeclaration(String constName, String value, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
			PHPClassConstData classConstData = PHPCodeDataFactory.createPHPClassConstData(constName, value, docInfo, null);
			classConstsList.add(classConstData);
		}

		public void handleIncludedFile(String includingType, String includeFileName, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition, int lineNumber) {
		}

		public void haveReturnValue() {
		}

		public void handleObjectInstansiation(String variableName, String className, String ctorArrguments, int line, int startPosition, boolean isUserDocumentation) {
		}

		public void handleVariableName(String variableName, int line) {
		}

		public void handleGlobalVar(String variableName) {
		}

		public void handleStaticVar(String variableName) {
		}

		public void startParsing(String fileName) {
		}

		public void finishParsing(int lastPosition, int lastLine, long lastModified) {

			PHPFunctionData[] arrayFunctions = new PHPFunctionData[functionsList.size() + functions.length];
			functionsList.toArray(arrayFunctions);
			System.arraycopy(functions, 0, arrayFunctions, functionsList.size(), functions.length);
			functions = arrayFunctions;
			for (PHPFunctionData element : functionsList) {
				functionsHash.put(element.getName(), element);
			}

			PHPClassData[] arrayClasses = new PHPClassData[classesList.size() + classes.length];
			classesList.toArray(arrayClasses);
			System.arraycopy(classes, 0, arrayClasses, classesList.size(), classes.length);
			classes = arrayClasses;
			for (PHPClassData element : classesList) {
				classesHash.put(getNormalizedString(element.getName()), element);
			}

			PHPConstantData[] arrayConstants = new PHPConstantData[constansList.size() + constants.length];
			constansList.toArray(arrayConstants);
			System.arraycopy(constants, 0, arrayConstants, constansList.size(), constants.length);
			constants = arrayConstants;
		}

		public void handleDefine(String name, String value, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
			if (name.startsWith("\"") || name.startsWith("\'")) { //$NON-NLS-1$ //$NON-NLS-2$
				name = name.substring(1);
			}
			if (name.endsWith("\"") || name.endsWith("\'")) { //$NON-NLS-1$ //$NON-NLS-2$
				name = name.substring(0, name.length() - 1);
			}

			constansList.add(PHPCodeDataFactory.createPHPConstantData(name, value, null, docInfo));
		}

		public void handleError(String description, int startPosition, int endPosition, int lineNumber) {
		}

		public void handleSyntaxError(int currToken, String currText, short[] rowOfProbe, int startPosition, int endPosition, int lineNumber) {
		}

		public void handleTask(String taskName, String description, int startPosition, int endPosition, int lineNumber) {
		}

		public void handlePHPStart(int startOffset, int endOffset) {
		}

		public void handlePHPEnd(int startOffset, int endOffset) {
		}

		public void setFirstDocBlock(PHPDocBlock docBlock) {
		}
	}
	
	private static final String TYPE_STRING = "string";
	private static final String TYPE_INT = "int";
	private static final String TYPE_BOOL = "bool";
	private static final String TYPE_OBJECT = "object";
	private static final String TYPE_MIXED = "mixed";
	private static final String TYPE_ARRAY = "array";
	private static final String TYPE_RESOURCE = "resource";
	private static final String TYPE_VOID = "void";
	
}
