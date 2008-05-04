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

import java.util.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.php.internal.core.phpModel.phpElementData.*;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPFunctionData.PHPFunctionParameter;

public abstract class DefaultParserClient extends ContextParserClient {

	protected PHPUserModel userModel = null;
	protected PHPProjectModel projectModel;
	protected String workingFileName;
	protected List functions;
	protected List classVars;
	protected List classConsts;
	protected List classFunctions;
	protected List classes;
	protected List includeFiles;
	protected List markers;
	protected List functionParameters;
	protected List phpTags;
	protected List constants;
	protected PHPDocBlock firstPHPDocBlock;
	protected Stack functionsStack;
	protected Stack classesStack;
	protected Stack classVarsStack;
	protected Stack classConstsStack;
	protected Stack classFunctionsStack;

	protected VariableContextBuilder variableContextBuilder;

	private boolean hadReturnStatement;

	public DefaultParserClient(PHPUserModel userModel, IProject project) {
		functions = new ArrayList();
		classVars = new ArrayList();
		classConsts = new ArrayList();
		classFunctions = new ArrayList();
		classes = new ArrayList();
		includeFiles = new ArrayList();
		markers = new ArrayList();
		functionParameters = new ArrayList();
		phpTags = new ArrayList();
		constants = new ArrayList();
		functionsStack = new Stack();
		classesStack = new Stack();
		classVarsStack = new Stack();
		classConstsStack = new Stack();
		classFunctionsStack = new Stack();
		hadReturnStatement = false;
		variableContextBuilder = new VariableContextBuilder();
		workingFileName = null;

		this.projectModel = PHPWorkspaceModelManager.getInstance().getModelForProject(project);
		if (this.projectModel == null) {
			this.projectModel = PHPWorkspaceModelManager.getDefaultPHPProjectModel();
		}
		this.userModel = userModel;
	}

	public void handleFunctionParameter(String classType, String variableName, boolean isReference, boolean isConst, String defaultValue, int startPosition, int endPosition, int stopPosition, int lineNumber) {
		variableName = variableName.substring(1);
		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, stopPosition, stopPosition, lineNumber);
		PHPFunctionData.PHPFunctionParameter parameter = PHPCodeDataFactory.createPHPFunctionParameter(variableName, userData, isReference, isConst, classType, defaultValue);
		functionParameters.add(parameter);
	}

	private static PHPFunctionData.PHPFunctionParameter getParameter(PHPFunctionData.PHPFunctionParameter[] parameters, String parameterName) {
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

	public void handleFunctionDeclaration(String functionName, boolean isClassFunction, int modifier, PHPDocBlock docInfo, int startPosition, int stopPosition, int lineNumber) {

		/**
		 * Bugfix: #160672.
		 * Check whether the function already exists, when parser tries to interpret it in different ways as a cause of parse errors in the file.
		 */
		if (!functionsStack.isEmpty()) {
			PHPFunctionData lastFunction = (PHPFunctionData) functionsStack.peek();
			if (functionName.equals(lastFunction.getName()) && startPosition == lastFunction.getUserData().getStartPosition()) {
				return;
			}
		}

		PHPFunctionData.PHPFunctionParameter[] parameters = new PHPFunctionData.PHPFunctionParameter[functionParameters.size()];
		functionParameters.toArray(parameters);
		functionParameters.clear();

		String returnType = null;

		if (docInfo != null) {
			Iterator it = docInfo.getTags(PHPDocTag.PARAM);
			while (it.hasNext()) {
				PHPDocTag param = (PHPDocTag) it.next();
				String arg = param.getValue().trim();
				String[] values = arg.split(" ");
				String name = null;
				String type = null;

				int length = values.length > 2 ? 2 : values.length;
				for (int i = 0; i < length; i++) {
					if (values[i].equals("") && length < values.length) {
						length++;
					}
					if (values[i].startsWith("$")) {
						name = values[i];
					} else if (type == null) {
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
					String originalClassType = parameter.getClassType();
					if (originalClassType == null || originalClassType.length() == 0) {
						parameter.setClassType(type);
					}
				}
			}
			Iterator returnIt = docInfo.getTags(PHPDocTag.RETURN);
			returnType = returnIt.hasNext() ? (String) ((PHPDocTag) returnIt.next()).getValue() : null;
		}

		if (returnType == null) {
			if (isClassFunction && functionName.equals(getCurrentClassName())) {
				returnType = getCurrentClassName();
			} else {
				returnType = "void";
			}
		} else {
			// cut the return type after the whitespace
			// The return type should ignore the description of the return type
			returnType = returnType.split("\\s")[0];
		}

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, stopPosition, stopPosition, lineNumber);
		PHPFunctionData functionData = PHPCodeDataFactory.createPHPFuctionData(functionName, modifier, docInfo, userData, parameters, returnType);
		if (isClassFunction) {
			classFunctions.add(functionData);
		} else {
			functions.add(functionData);
		}

		functionsStack.push(functionData);
		for (PHPFunctionParameter element : parameters) {
			((PHPCodeDataFactory.PHPFunctionParameterImp) element).setContainer(functionData);
			variableContextBuilder.addVariable(getContext(), element);
			variableContextBuilder.addObjectInstantiation(getContext(), element.getName(), element.getClassType(), false, 0, startPosition);
		}
	}

	public void handleFunctionDeclarationEnds(boolean isClassFunction, int endPosition) {
		String functionName = null;
		if (!functionsStack.isEmpty()) {
			PHPFunctionData lastFunction = (PHPFunctionData) functionsStack.peek();
			if (lastFunction != null) {
				functionName = lastFunction.getName();
			}
		}
		handleFunctionDeclarationEnds(functionName, isClassFunction, endPosition);
	}

	private void handleFunctionDeclarationEnds(String functionName, boolean isClassFunction, int endPosition) {
		PHPFunctionData lastFunction = null;
		boolean wasEmpty = functionsStack.isEmpty();
		if (!wasEmpty) {
			lastFunction = (PHPFunctionData) functionsStack.pop();
		}
		if (lastFunction != null) {
			if (lastFunction.getName().equals(functionName)) {
				if (hadReturnStatement) {
					if (lastFunction.getReturnType().equals("void")) {
						((PHPCodeDataFactory.PHPFunctionDataImp) lastFunction).setReturnType("unknown");
					}
				}
				((PHPCodeDataFactory.UserDataImp) lastFunction.getUserData()).setEndPosition(endPosition);
			}
		}

		hadReturnStatement = false;
		super.handleFunctionDeclarationEnds(isClassFunction, endPosition);
	}

	public void handleClassDeclaration(String className, int modifier, String superClassName, String interfacesNames, PHPDocBlock docInfo, int startPosition, int stopPosition, int lineNumber) {
		PHPClassData.PHPSuperClassNameData superClassData;
		if (superClassName != null) {
			int index = superClassName.indexOf(']');
			String locationStr = superClassName.substring(1, index);
			superClassName = superClassName.substring(index + 1);
			int superClassStartPosition = Integer.parseInt(locationStr.substring(0, locationStr.indexOf(".")));
			int superClassEndPosition = Integer.parseInt(locationStr.substring(locationStr.indexOf(".") + 1, locationStr.length()));
			UserData userData = PHPCodeDataFactory.createUserData(workingFileName, superClassStartPosition, superClassEndPosition, superClassStartPosition, 0);
			superClassData = PHPCodeDataFactory.createPHPSuperClassNameData(superClassName, userData);
		} else {
			superClassData = PHPCodeDataFactory.createPHPSuperClassNameData(superClassName, null);
		}

		PHPClassData.PHPInterfaceNameData[] interfaces;
		if (interfacesNames != null) {
			String[] interfacesNamesArray = interfacesNames.split(",");
			interfaces = new PHPClassData.PHPInterfaceNameData[interfacesNamesArray.length];
			for (int i = 0; i < interfacesNamesArray.length; i++) {

				String interfaceName = interfacesNamesArray[i];
				int index = interfaceName.indexOf(']');
				String locationStr = interfaceName.substring(1, index);
				interfaceName = interfaceName.substring(index + 1);
				int interfaceNameStartPosition = Integer.parseInt(locationStr.substring(0, locationStr.indexOf(".")));
				int interfaceNameEndPosition = Integer.parseInt(locationStr.substring(locationStr.indexOf(".") + 1, locationStr.length()));
				UserData userData = PHPCodeDataFactory.createUserData(workingFileName, interfaceNameStartPosition, interfaceNameEndPosition, interfaceNameStartPosition, 0);
				interfaces[i] = PHPCodeDataFactory.createPHPInterfaceNameData(interfaceName, userData);
			}

		} else {
			interfaces = PHPCodeDataFactory.EMPTY_INTERFACES_DATA_ARRAY;
		}

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, stopPosition, stopPosition, lineNumber);

		PHPClassData classData = PHPCodeDataFactory.createPHPClassData(className, modifier, docInfo, userData, superClassData, interfaces, PHPCodeDataFactory.EMPTY_CLASS_VAR_DATA_ARRAY, PHPCodeDataFactory.EMPTY_CLASS_CONST_DATA_ARRAY, PHPCodeDataFactory.EMPTY_FUNCTIONS_DATA_ARRAY);

		classes.add(classData);
		classesStack.push(classData);
	}

	public void hadleClassDeclarationStarts(String className, int startPosition) {
		// push the unclosed class to the stack.
		if (getCurrentClassName() != null && getCurrentClassName().length() != 0) {
			classVarsStack.push(classVars);
			classConstsStack.push(classConsts);
			classFunctionsStack.push(classFunctions);
			classVars = new ArrayList();
			classConsts = new ArrayList();
			classFunctions = new ArrayList();
		}
		super.hadleClassDeclarationStarts(className, startPosition);
	}

	public void handleClassDeclarationEnds(String className, int endPosition) {
		if (!classesStack.isEmpty()) {
			PHPCodeDataFactory.PHPClassDataImp classData = (PHPCodeDataFactory.PHPClassDataImp) classesStack.pop();
			if (classData.getName().equals(className)) {
				PHPClassVarData[] vars = new PHPClassVarData[classVars.size()];
				classVars.toArray(vars);
				Arrays.sort(vars);

				PHPClassConstData[] consts = new PHPClassConstData[classConsts.size()];
				classConsts.toArray(consts);
				Arrays.sort(consts);

				PHPFunctionData[] func = new PHPFunctionData[classFunctions.size()];
				classFunctions.toArray(func);
				Arrays.sort(func);

				for (PHPClassVarData element : vars) {
					((PHPCodeDataFactory.PHPClassVarDataImp) element).setContainer(classData);
				}
				for (PHPClassConstData element : consts) {
					((PHPCodeDataFactory.PHPClassConstDataImp) element).setContainer(classData);
				}
				for (PHPFunctionData element : func) {
					((PHPCodeDataFactory.PHPFunctionDataImp) element).setContainer(classData);
				}

				classData.setFunctions(func);
				classData.setVars(vars);
				classData.setConsts(consts);

				((PHPCodeDataFactory.UserDataImp) classData.getUserData()).setEndPosition(endPosition);
			}
		}
		if (!classVarsStack.isEmpty()) {
			classVars = (List) classVarsStack.pop();
			classConsts = (List) classConstsStack.pop();
			classFunctions = (List) classFunctionsStack.pop();
		} else {
			classVars.clear();
			classConsts.clear();
			classFunctions.clear();
		}
		super.handleClassDeclarationEnds(className, endPosition);
	}

	public void handleClassVariablesDeclaration(String variables, int modifier, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, stopPosition, 0);
		String classType = null;
		if (docInfo != null) {
			Iterator it = docInfo.getTags(PHPDocTag.VAR);
			while (it.hasNext()) {
				PHPDocTag varTag = (PHPDocTag) it.next();
				String value = varTag.getValue().trim();
				String[] values = value.split(" ");
				classType = values[0];
			}
		}
		StringTokenizer t = new StringTokenizer(variables, ",", false);
		while (t.hasMoreTokens()) {
			String var = t.nextToken().substring(1);
			PHPClassVarData classVarData = PHPCodeDataFactory.createPHPClassVarData(var, modifier, classType, docInfo, userData);
			handleObjectInstansiation(var, classType, null, 0, 0, false);
			classVars.add(classVarData);
		}
	}

	public void handleClassConstDeclaration(String constName, String value, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, stopPosition, 0);
		PHPClassConstData classConstData = PHPCodeDataFactory.createPHPClassConstData(constName, value, docInfo, userData);
		classConsts.add(classConstData);
	}

	public void handleIncludedFile(String includingType, String includeFileName, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition, int lineNumber) {
		if (includeFileName == null || includeFileName.length() == 0) {
			return;
		}

		if (includeFileName.charAt(0) == '\'' || includeFileName.charAt(0) == '\"') {
			includeFileName = includeFileName.substring(1, includeFileName.length() - 1);
		} else {
			// Unknown, can be a variable or a constant.
			return;
		}

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, stopPosition, lineNumber);
		PHPIncludeFileData include = PHPCodeDataFactory.createPHPIncludeFileData(includingType, includeFileName, docInfo, userData);
		includeFiles.add(include);
	}

	public void handleError(String description, int startPosition, int endPosition, int lineNumber) {
	}

	public void haveReturnValue() {
		hadReturnStatement = true;
	}

	public void handleObjectInstansiation(String variableName, String className, String ctorArrguments, int line, int startPosition, boolean isUserDocumentation) {
		if (variableName == null) { // this can happen when there is a parsing
			// error (in the user code).
			return;
		}
		variableContextBuilder.addObjectInstantiation(getContext(), variableName, className, isUserDocumentation, line, startPosition);
	}

	public void handleVariableName(String variableName, int line) {
		variableContextBuilder.addVariable(getContext(), variableName);
	}

	public void handleGlobalVar(String variableName) {
		PHPVariableData variable = PHPCodeDataFactory.createPHPVariableData(variableName.substring(1), true, null, null);
		variableContextBuilder.addVariable(getContext(), variable);
	}

	public void startParsing(String fileName) {
		super.startParsing(fileName);
		workingFileName = fileName;
		variableContextBuilder = new VariableContextBuilder();

		functions.clear();
		classes.clear();
		classVars.clear();
		classConsts.clear();
		classFunctions.clear();
		includeFiles.clear();
		markers.clear();
		phpTags.clear();
		constants.clear();
		functionParameters.clear();
		functionsStack.clear();
		classesStack.clear();
		classVarsStack.clear();
		classConstsStack.clear();
		classFunctionsStack.clear();
	}

	private void restoreToDefaultContext(int endPosition) {
		final String currentClassName = getCurrentClassName();
		final boolean isInsideClass = currentClassName.length() > 0;
		final String currentFunctionName = getCurrentFunctionName();
		if (currentFunctionName.length() > 0) {
			endPosition = getEndPosition(endPosition, currentFunctionName, isInsideClass ? classFunctions : functions);
			handleFunctionDeclarationEnds(currentFunctionName, isInsideClass, endPosition);
			restoreToDefaultContext(endPosition);
		}
		if (isInsideClass) {
			endPosition = getEndPosition(endPosition, currentClassName, classes);
			handleClassDeclarationEnds(currentClassName, endPosition);
			restoreToDefaultContext(endPosition);
		}
	}

	//This method gets the end position in case there's a syntax error and
	//a class OR a function are not closed properly
	private int getEndPosition(int endPosition, final String currentElementName, Collection repository) {
		for (Iterator iter = repository.iterator(); iter.hasNext();) {
			PHPCodeData element = (PHPCodeData) iter.next();
			if (currentElementName.equals(element.getName())) {
				UserData elementUserData = element.getUserData();
				int elementStartPosition = elementUserData.getStartPosition();
				int phpStartPosition = 0;
				for (Iterator iterator = phpTags.iterator(); iterator.hasNext();) {
					UserData phpUserData = (UserData) iterator.next();
					phpStartPosition = phpUserData.getStartPosition();
					if (phpStartPosition > elementStartPosition) {
						return phpStartPosition;
					}
				}
			}
		}
		return endPosition;
	}

	public void finishParsing(int lastPosition, int lastLine, long lastModified) {
		restoreToDefaultContext(lastPosition);

		PHPClassData[] allClasses = PHPCodeDataFactory.EMPTY_CLASS_DATA_ARRAY;
		if (!classes.isEmpty()) {
			allClasses = new PHPClassData[classes.size()];
			classes.toArray(allClasses);
		}

		PHPFunctionData[] allFunctions = PHPCodeDataFactory.EMPTY_FUNCTIONS_DATA_ARRAY;
		if (!functions.isEmpty()) {
			allFunctions = new PHPFunctionData[functions.size()];
			functions.toArray(allFunctions);
		}

		PHPIncludeFileData[] allIncludes = PHPCodeDataFactory.EMPTY_INCLUDE_DATA_ARRAY;
		if (!includeFiles.isEmpty()) {
			allIncludes = new PHPIncludeFileData[includeFiles.size()];
			includeFiles.toArray(allIncludes);
		}

		PHPConstantData[] allConstants = PHPCodeDataFactory.EMPTY_CONSTANT_DATA_ARRAY;
		if (!constants.isEmpty()) {
			allConstants = new PHPConstantData[constants.size()];
			constants.toArray(allConstants);
		}

		IPHPMarker[] allMarkers = PHPCodeDataFactory.EMPTY_MARKERS_DATA_ARRAY;
		if (!markers.isEmpty()) {
			allMarkers = new IPHPMarker[markers.size()];
			markers.toArray(allMarkers);
		}

		if (phpTags.size() % 2 == 1) {
			phpTags.add(PHPCodeDataFactory.createUserData(workingFileName, lastPosition, lastPosition, lastPosition, 0));
		}

		PHPBlock[] phpBlocks = new PHPBlock[phpTags.size() + 1 >> 1]; // we
		// want
		// to
		// round
		// the
		// result
		// up
		for (int i = 0; i < phpBlocks.length; i++) {
			int p = i << 1;
			UserData startTag = (UserData) phpTags.get(p);
			UserData endTag;
			if (p + 1 < phpTags.size()) {
				endTag = (UserData) phpTags.get(p + 1);
			} else {
				endTag = (UserData) phpTags.get(p);
			}
			phpBlocks[i] = new PHPBlock(startTag, endTag);
		}

		fixObjectInstantiation(allClasses, allFunctions);
		PHPVariablesTypeManager variablesTypeManager = variableContextBuilder.getPHPVariablesTypeManager();

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, 0, 0, 0, lastLine);

		PHPFileData fileData = PHPCodeDataFactory.createPHPFileData(workingFileName, userData, allClasses, allFunctions, variablesTypeManager, allIncludes, allConstants, allMarkers, phpBlocks, firstPHPDocBlock, lastModified);

		for (PHPClassData element : allClasses) {
			element.setContainer(fileData);
		}

		for (PHPFunctionData element : allFunctions) {
			element.setContainer(fileData);
		}

		for (PHPConstantData element : allConstants) {
			element.setContainer(fileData);
		}

		for (PHPIncludeFileData element : allIncludes) {
			element.setContainer(fileData);
		}

		userModel.insert(fileData);
	}

	/**
	 * This function goes over all the object instantiation and tries to find
	 * for the follwing assignment the target's new type. and then fixes the
	 * database accordingly. $a = $b; $a = foo(); $a = MyClass::foo(); $a =
	 * $b->f(); / $a = $b->c;
	 *
	 * @param cls
	 *            the classes that was created in this parsing (not in the model
	 *            yet)
	 * @param func
	 *            the function that was created in this parsing (still not in
	 *            the model yet)
	 */

	protected void fixObjectInstantiation(PHPClassData[] cls, PHPFunctionData[] func) {
		CodeData[] projectClasses = projectModel.getClasses();
		CodeData[] projectFunctions = projectModel.getFunctions();
		PHPVariablesTypeManager variablesTypeManager = variableContextBuilder.getPHPVariablesTypeManager();
		Map variablesInstansiations = variablesTypeManager.getVariablesInstansiation();

		List variablesNames = new LinkedList();
		List contextes = new LinkedList();
		List classNames = new LinkedList();
		List lines = new LinkedList();
		List positions = new LinkedList();
		List userDocumentations = new LinkedList();

		Set keys = variablesInstansiations.keySet();
		Iterator keysIterator = keys.iterator();

		while (keysIterator.hasNext()) {
			String key = (String) keysIterator.next();
			String[] variableContext = key.split(";");

			String variableName = null;
			String contextClassName = null;
			String contextFunctionName = null;

			// the following code has a bug if it deals with the following code:
			// function f() {
			// $a->b = $c;
			// }
			// but its OK since we dont need to support them here - its being
			// fixed in the codeCompletion
			if (variableContext[0].equals("this") && variableContext.length > 3) {
				//this part solves the case were $this->a = $b;
				int length = variableContext.length;
				contextFunctionName = variableContext[length - 1];
				contextClassName = variableContext[length - 2];
				for (int i = length - 3; i >= 0; i--) {
					if (variableContext[i].equals("null")) {
						continue;
					}
					variableName = variableName == null ? variableContext[i] : variableContext[i] + ";" + variableName;
				}
			} else {
				if (variableContext.length >= 3) {
					contextFunctionName = variableContext[2];
				}
				if (variableContext.length >= 2) {
					contextClassName = variableContext[1];
				}
				if (variableContext.length >= 1) {
					variableName = variableContext[0];
				}
			}

			PHPCodeContext codeContext = ModelSupport.createContext(contextClassName, contextFunctionName);

			// going over all of the (same) object instantiations
			List list = (List) variablesInstansiations.get(key);
			Iterator listIter = list.iterator();
			while (listIter.hasNext()) {
				PHPVariableTypeData variableTypeData = (PHPVariableTypeData) listIter.next();
				String className = variableTypeData.getType();
				int lineNumber = variableTypeData.getLine();
				int position = variableTypeData.getPosition();
				boolean isUserDocumentation = variableTypeData.isUserDocumentation();
				if (className != null && className.startsWith("r_variable")) {
					className = getClassName(className, position, lineNumber, cls, func, projectClasses, projectFunctions);
				}
				if ("self".equalsIgnoreCase(className)) {
					className = codeContext.getContainerClassName();
				}
				variablesNames.add(variableName);
				contextes.add(codeContext);
				classNames.add(className);
				lines.add(new Integer(lineNumber));
				positions.add(new Integer(position));
				userDocumentations.add(Boolean.valueOf(isUserDocumentation));
			}
		}

		// clearing the DB
		variablesInstansiations.clear();

		// coping all the updated data back to the database.
		Iterator contextesIterator = contextes.iterator();
		Iterator variablesNamesIterator = variablesNames.iterator();
		Iterator classNamesIterator = classNames.iterator();
		Iterator linesIterator = lines.iterator();
		Iterator positionsIterator = positions.iterator();
		Iterator userDocumentationsIterator = userDocumentations.iterator();

		while (contextesIterator.hasNext()) {
			final String next = (String) variablesNamesIterator.next();
			variableContextBuilder.addObjectInstantiation((PHPCodeContext) contextesIterator.next(), next, (String) classNamesIterator.next(), ((Boolean) userDocumentationsIterator.next()).booleanValue(), ((Integer) linesIterator.next()).intValue(), ((Integer) positionsIterator.next()).intValue());
		}
	}

	private String getClassName(String className, int position, int lineNumber, PHPClassData[] cls, PHPFunctionData[] func, CodeData[] projectClasses, CodeData[] projectFunctions) {
		String[] classNameParts = className.split(";");
		String sourceClassName = null;
		int propertyNamePosition = 1;

		if (classNameParts.length < 2) {
			return null;
		}
		if (classNameParts.length == 2) {
			// meaning we are in a statement of $a = $b
			return PHPFileDataUtilities.getVariableType(workingFileName, classNameParts[1], position, lineNumber, userModel, true);
		}
		if (classNameParts[1].equals("function_call")) {
			// meaning it's $a = foo();
			sourceClassName = getFunctionReturnType(classNameParts[2], position, cls, func, projectClasses, projectFunctions);
			propertyNamePosition = 3;
		} else {
			propertyNamePosition = 2;
			sourceClassName = classNameParts[1];
			if (sourceClassName.charAt(0) == '$') {
				// meaning its $a = $b->foo()
				sourceClassName = PHPFileDataUtilities.getVariableType(workingFileName, sourceClassName, position, lineNumber, userModel, true);
			} // else its $a = MyClass::foo()
		}

		// this loop is for the case there is a nesting: $a = $b->foo()->bar()
		for (; propertyNamePosition < classNameParts.length; propertyNamePosition++) {
			if (!classNameParts[propertyNamePosition].equals("null")) {
				sourceClassName = getPropertyType(sourceClassName, classNameParts[propertyNamePosition], cls, func, projectClasses, projectFunctions);
				// the "-" stands for finding the property but couldn't find
				// it's type
				if (sourceClassName != null && sourceClassName.equals("-")) {
					sourceClassName = null;
					break;
				}
			}
		}
		return sourceClassName;
	}

	/**
	 * this function is for finding the return type of a standalone functions
	 * for example: $a = foo()
	 */
	private String getFunctionReturnType(String functionName, int position, PHPClassData[] classes, PHPFunctionData[] functions, CodeData[] projectClasses, CodeData[] projectFunctions) {
		for (PHPClassData cl : classes) {
			UserData userData = cl.getUserData();
			if (position > userData.getStartPosition() && position <= userData.getEndPosition()) {
				return getPropertyType(cl.getName(), functionName, classes, functions, projectClasses, projectFunctions);
			}
		}
		return getPropertyType(null, functionName, classes, functions, projectClasses, projectFunctions);

	}

	private String getPropertyType(String className, String propertyName, CodeData[] classes, CodeData[] functions, CodeData[] projectClasses, CodeData[] projectFunctions) {
		String rv;
		if (className == null && projectModel != null) {
			rv = getFunctionReturnType(propertyName, functions);
			if (rv == null) {
				rv = getFunctionReturnType(propertyName, projectFunctions);
			}
			return rv;
		}
		rv = innerGetPropertyType(className, propertyName, classes, functions);
		if (rv == null && projectModel != null) {
			// maybe the class is not in the current file but in the project
			rv = innerGetPropertyType(className, propertyName, projectClasses, projectFunctions);
		}
		return rv;
	}

	private String innerGetPropertyType(String className, String propertyName, CodeData[] classes, CodeData[] functions) {
		return innerGetPropertyType(className, propertyName, classes, functions, new HashMap<String, String>());
	}

	/**
	 * This function is an internal function developers should use the innerGetPropertyType version without the Map.
	 * @param subClasses uses as a stopping condition in case there's a loop in the inheritance tree.
	 */
	private String innerGetPropertyType(String className, String propertyName, CodeData[] classes, CodeData[] functions, Map<String, String> subClasses) {
		for (CodeData element : classes) {
			PHPClassData currClass = (PHPClassData) element;
			if (currClass.getName().equals(className)) {
				String rv;
				if (propertyName.charAt(0) == '*') {
					// meaning this is a class variable and not a function.
					// this * sign was added by the parser and not by the user
					PHPClassVarData[] classVars = currClass.getVars();
					rv = getVariableType(propertyName.substring(1), classVars);
				} else {
					// meaning its a function
					PHPFunctionData[] classFunctions = currClass.getFunctions();
					rv = getFunctionReturnType(propertyName, classFunctions);
				}
				if (rv == null && currClass.getSuperClassData() != null) {
					// trying to find in the ancestor
					String superClassName = currClass.getSuperClassData().getName();
					if (subClasses.containsKey(superClassName)) {
						return null;
					}
					subClasses.put(className, className);
					rv = innerGetPropertyType(superClassName, propertyName, classes, functions, subClasses);
				}
				return rv;
			}
		}
		return null;
	}

	private String getFunctionReturnType(String functionName, CodeData[] functions) {
		for (CodeData element : functions) {
			PHPFunctionData phpFunctionData = (PHPFunctionData) element;
			if (functionName.equals(phpFunctionData.getName())) {
				String returnType = phpFunctionData.getReturnType();
				if (returnType.equals("void") || returnType.equals("unknown")) {
					return "-";
				}
				return returnType;
			}
		}
		return null;
	}

	private String getVariableType(String variableName, CodeData[] variables) {
		for (CodeData element : variables) {
			PHPClassVarData classVarData = (PHPClassVarData) element;
			if (variableName.equals(classVarData.getName())) {
				String returnType = classVarData.getClassType();
				if (returnType != null && returnType.equals("unknown")) {
					return "-";
				}
				return returnType;
			}
		}
		return null;
	}

	public void handlePHPStart(int startOffset, int endOffset) {
		phpTags.add(PHPCodeDataFactory.createUserData(workingFileName, startOffset, endOffset, endOffset, 0));
	}

	public void handlePHPEnd(int startOffset, int endOffset) {
		phpTags.add(PHPCodeDataFactory.createUserData(workingFileName, startOffset, endOffset, startOffset, 0));
	}

	public void setFirstDocBlock(PHPDocBlock docBlock) {
		firstPHPDocBlock = docBlock;
	}

	public PHPDocBlock getFirstDocBlock() {
		return firstPHPDocBlock;
	}

	public void handleDefine(String name, String value, PHPDocBlock docInfo, int startPosition, int endPosition, int stopPosition) {
		char chr = name.charAt(0);
		if (chr == '\"' || chr == '\'') {
			name = name.substring(1);
		}
		chr = name.charAt(name.length() - 1);
		if (chr == '\"' || chr == '\'') {
			name = name.substring(0, name.length() - 1);
		}

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, stopPosition, 0);
		constants.add(PHPCodeDataFactory.createPHPConstantData(name, value, userData, docInfo));
	}

	public void handleSyntaxError(int currToken, String currText, short[] rowOfProbe, int startPosition, int endPosition, int lineNumber) {
		String unexpectedString = "";
		boolean addUnexpected;

		if (currToken == getEOFTag()) {
			addUnexpected = true;
			unexpectedString = "End of File";
			startPosition = --endPosition;
		} else if (currToken == getCONSTANT_ENCAPSED_STRINGTag()) {
			addUnexpected = true;
			endPosition = startPosition + currText.trim().length();
			unexpectedString = "String";
		} else {
			addUnexpected = currText != null && currText.trim().length() > 0;
			if (addUnexpected) {
				unexpectedString = currText.trim();
				endPosition = startPosition + unexpectedString.length();
				unexpectedString = '\'' + unexpectedString + '\'';
			}
		}

		//IntList list = new IntList();
		List list = new ArrayList();
		for (int probe = 0; probe < rowOfProbe.length; probe += 2) {
			int curr = rowOfProbe[probe];
			String value = getConstantValue(curr);
			if (value != null && !value.equals("")) {
				list.add(value);
			}
		}
		int listSize = list.size();
		if (listSize > 3) {
			listSize = 0;
		}

		String description = "";

		if (!addUnexpected) {
			switch (listSize) {
				case 0:
					description = "Syntax Error";
					break;
				case 1:
					description = "Syntax Error: expecting: " + list.get(0);
					break;
				case 2:
					description = "Syntax Error: expecting: " + list.get(0) + " or " + list.get(1);
					break;
				case 3:
					description = "Syntax Error: expecting: " + list.get(0) + " or " + list.get(1) + " or " + list.get(2);
					break;
			}
		} else {
			switch (listSize) {
				case 0:
					description = "Syntax Error: unexpected " + unexpectedString;
					break;
				case 1:
					description = "Syntax Error: unexpected " + unexpectedString + ", expecting: " + list.get(0);
					break;
				case 2:
					description = "Syntax Error: unexpected " + unexpectedString + ", expecting: " + list.get(0) + " or " + list.get(1);
					break;
				case 3:
					description = "Syntax Error: unexpected " + unexpectedString + ", expecting: " + list.get(0) + " or " + list.get(1) + " or " + list.get(2);
					break;
			}
		}

		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, startPosition, lineNumber);
		markers.add(new PHPMarker(IPHPMarker.ERROR, description, userData));
	}

	public void handleTask(String taskName, String description, int startPosition, int endPosition, int lineNumber) {
		UserData userData = PHPCodeDataFactory.createUserData(workingFileName, startPosition, endPosition, startPosition, lineNumber);
		markers.add(new PHPTask(taskName, description, userData));
	}

	private String getConstantValue(int tag) {
		String rv = getError(tag);
		if (rv != null) {
			return '\'' + rv + '\'';
		}
		if (tag == getStringTag()) {
			return "Identifier";
		}
		if (tag == getVariableTag()) {
			return "Variable";
		}

		return null;
	}

	protected abstract String getError(int tag);

	protected abstract int getStringTag();

	protected abstract int getVariableTag();

	protected abstract int getEOFTag();

	protected abstract int getCONSTANT_ENCAPSED_STRINGTag();

}