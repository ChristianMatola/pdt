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
package org.eclipse.php.internal.core.phpModel;

import java.io.File;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.php.internal.core.documentModel.provisional.contenttype.ContentTypeIdForPHP;
import org.eclipse.php.internal.core.phpModel.parser.*;
import org.eclipse.php.internal.core.phpModel.parser.PHPIncludePathModel.IncludePathModelType;
import org.eclipse.php.internal.core.phpModel.phpElementData.*;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPClassData.PHPInterfaceNameData;
import org.eclipse.php.internal.core.phpModel.phpElementData.PHPClassData.PHPSuperClassNameData;
import org.eclipse.php.internal.core.project.options.includepath.IncludePathVariableManager;
import org.eclipse.php.internal.core.resources.ExternalFileWrapper;
import org.eclipse.php.internal.core.resources.ExternalFilesRegistry;
import org.eclipse.php.internal.core.util.text.StringUtils;

public class PHPModelUtil {

	public static final String PHPDOC_CLASS_NAME_SEPARATOR = "\\|"; //$NON-NLS-1$

	public static PHPFunctionData getRealConstructor(PHPProjectModel projectModel, String fileName, PHPClassData classData) {
		if (classData.hasConstructor()) {
			return classData.getConstructor();
		}
		CodeData parentConstructor = projectModel.getClassFunctionData(fileName, classData.getName(), PHPClassData.CONSTRUCTOR);
		if (parentConstructor != null)
			return (PHPFunctionData) parentConstructor;
		return classData.getConstructor();
	}

	public static PHPFunctionData getRealDestructor(PHPProjectModel projectModel, String fileName, PHPClassData classData) {
		CodeData parentDestructor = projectModel.getClassFunctionData(fileName, classData.getName(), PHPClassData.DESTRUCTOR);
		if (parentDestructor != null)
			return (PHPFunctionData) parentDestructor;
		return null;
	}

	/**
	 * finding the return type of the function.
	 */
	public static String getFunctionReturnType(PHPProjectModel projectModel, String fileName, String className, String functionName) {
		if (className == null) {

			// checking if its a non-class function from within the file
			PHPFunctionData function = projectModel.getFunction(fileName, functionName);
			if (function != null)
				return function.getReturnType();

			// checking if its a non-class function from within the project
			CodeData[] functions = projectModel.getFunctions();
			for (CodeData projectFunction : functions) {
				if (projectFunction.getName().equals(functionName)) {
					if (projectFunction instanceof PHPFunctionData) {
						return ((PHPFunctionData) projectFunction).getReturnType();
					}
				}
			}
			return null;
		}
		String[] realClassNames = className.split(PHPDOC_CLASS_NAME_SEPARATOR);
		Set<String> functionReturnClassNames = new LinkedHashSet<String>();
		for (String realClassName : realClassNames) {
			realClassName = realClassName.trim();
			CodeData classFunction = projectModel.getClassFunctionData(fileName, realClassName, functionName);
			if (classFunction != null) {
				if (classFunction instanceof PHPFunctionData) {
					functionReturnClassNames.add(((PHPFunctionData) classFunction).getReturnType());
				}
				continue;
			}

			// checking if the function belongs to one of the class's ancestor
			PHPClassData classData = projectModel.getClass(fileName, realClassName);

			if (classData == null) {
				continue;
			}
			String functionReturnClassName = null;
			PHPClassData.PHPSuperClassNameData superClassNameData = classData.getSuperClassData();
			if (superClassNameData != null) {
				String superClassName = superClassNameData.getName();
				if (superClassName != null) {
					functionReturnClassName = getFunctionReturnType(projectModel, fileName, superClassName, functionName);
				}
			}
			if (functionReturnClassName != null) {
				functionReturnClassNames.add(functionReturnClassName);
			}
		}
		return StringUtils.implodeStrings(functionReturnClassNames, "|"); //$NON-NLS-1$
	}

	/**
	 * finding the type of the class variable.
	 */
	public static String getVarType(PHPProjectModel projectModel, String fileName, String className, String varName, int statementStart, int line, boolean determineObjectTypeFromOtherFile) {
		String tempType = PHPFileDataUtilities.getVariableType(fileName, "this;*" + varName, statementStart, line, projectModel.getPHPUserModel(), determineObjectTypeFromOtherFile); //$NON-NLS-1$
		if (tempType != null) {
			return tempType;
		}

		// process multiple classes variables and compile their types for recursive multiple resolution
		String[] realClassNames = className.split(PHPDOC_CLASS_NAME_SEPARATOR);
		Set<String> varClassNames = new LinkedHashSet<String>();
		for (String realClassName : realClassNames) {
			realClassName = realClassName.trim();
			CodeData classVar = projectModel.getClassVariablesData(fileName, realClassName, varName);
			if (classVar != null) {
				if (classVar instanceof PHPClassVarData) {
					varClassNames.add(((PHPClassVarData) classVar).getClassType());
				}
				continue;
			}
			// checking if the variable belongs to one of the class's ancestor
			PHPClassData classData = projectModel.getClass(fileName, realClassName);

			if (classData == null) {
				continue;
			}
			PHPClassData.PHPSuperClassNameData superClassNameData = classData.getSuperClassData();
			if (superClassNameData == null) {
				continue;
			}
			String classVarClassName = getVarType(projectModel, fileName, superClassNameData.getName(), varName, statementStart, line, determineObjectTypeFromOtherFile);
			if (classVarClassName != null) {
				varClassNames.add(classVarClassName);
			}
		}
		return StringUtils.implodeStrings(varClassNames, "|"); //$NON-NLS-1$
	}

	public static class PHPContainerStringConverter {
		public static Object toContainer(final String sPath) {
			final IPath path = Path.fromPortableString(sPath);
			if (path == null)
				return null;
			IFile file = null;
			try {
				file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
			} catch (final IllegalArgumentException e) {
			}
			if (file != null) {
				final PHPFileData fileData = PHPWorkspaceModelManager.getInstance().getModelForFile(file, false);
				if (fileData != null)
					return fileData;
			}
			IFolder folder = null;
			try {
				folder = ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
			} catch (final IllegalArgumentException e) {
			}
			if (folder != null)
				return folder;
			IProject project = null;
			try {
				project = ResourcesPlugin.getWorkspace().getRoot().getProject(path.segments()[0]);
			} catch (final IllegalArgumentException e) {
			}
			return project;
		}

		public static String toString(Object phpElement) {
			if (phpElement instanceof PHPCodeData)
				while (phpElement != null && !(phpElement instanceof PHPFileData))
					phpElement = ((PHPCodeData) phpElement).getContainer();
			if (phpElement == null)
				return ""; //$NON-NLS-1$
			IResource res;
			IPath path;
			if ((res = getResource(phpElement)) != null && (path = res.getFullPath()) != null)
				return path.toPortableString();
			return ""; //$NON-NLS-1$
		}
	}

	public static PHPCodeData getElementAt(final PHPCodeData elementData, final int offset) {
		// why?
		throw new RuntimeException("test me"); //$NON-NLS-1$
	}

	public static Object getExternalResource(final Object element, final IProject project) {
		if (!(element instanceof PHPCodeData))
			return null;
		PHPCodeData codeData = (PHPCodeData) element;
		PHPFileData fileData = PHPModelUtil.getPHPFileContainer(codeData);
		if (fileData == null)
			return null;
		String fileName = fileData.getName();
		final File file = new File(fileName);
		if (file != null && file.exists())
			return file;
		PHPProjectModel projectModel = null;
		if (project == null) {
			fileName = fileData.getName();
			final PHPProjectModel[] projectModels = PHPWorkspaceModelManager.getInstance().listModels();
			for (PHPProjectModel otherProjectModel : projectModels)
				if (otherProjectModel.getFileData(fileName) == fileData) {
					projectModel = otherProjectModel;
					break;
				}

		} else
			projectModel = PHPWorkspaceModelManager.getInstance().getModelForProject(project);

		if (projectModel == null)
			return null;
		return projectModel.getExternalResource(fileData);
	}

	public static CodeData[] getMatchingClasses(final IPhpModel model, final String className) {
		return model.getClass(className.toLowerCase());
	}

	public static PHPCodeData[] getMatchingElements(final IPhpModel model, final String elementName) {
		final CodeData[] matchingClasses = getMatchingClasses(model, elementName);
		final PHPFunctionData[] matchingFunctions = getMatchingFunctions(model, elementName);
		final int nMatchingClasses = matchingClasses.length;
		final int nMatchingFunctions = matchingFunctions.length;
		if (nMatchingClasses + nMatchingFunctions == 0)
			return new PHPCodeData[] {};
		final ArrayList matchingElements = new ArrayList(nMatchingClasses + nMatchingFunctions);
		if (nMatchingClasses > 0)
			matchingElements.addAll(Arrays.asList(matchingClasses));
		if (nMatchingFunctions > 0)
			matchingElements.addAll(Arrays.asList(matchingFunctions));
		return (PHPCodeData[]) matchingElements.toArray(new PHPCodeData[] {});
	}

	public static PHPFunctionData[] getMatchingFunctions(final IPhpModel model, final String functionName) {
		final CodeData[] functions = model.getFunctions(functionName);
		PHPFunctionData aFunction;
		final ArrayList matchingFunctions = new ArrayList(1);
		for (int i = 0; i < functions.length; ++i) {
			aFunction = (PHPFunctionData) functions[i];
			if (functionName.equalsIgnoreCase(aFunction.getName()))
				matchingFunctions.add(aFunction);
		}
		return (PHPFunctionData[]) matchingFunctions.toArray(new PHPFunctionData[] {});
	}

	public static int getModifier(final PHPCodeData member) {
		if (member instanceof PHPClassData) {
			final PHPClassData cls = (PHPClassData) member;
			return cls.getModifiers();
		}
		if (member instanceof PHPFunctionData) {
			final PHPFunctionData func = (PHPFunctionData) member;
			return func.getModifiers();
		}
		if (member instanceof PHPClassVarData) {
			final PHPClassVarData var = (PHPClassVarData) member;
			return var.getModifiers();
		}

		return 0;
	}

	public static Object getParent(final Object element) {

		// try to map resources to the containing source folder
		if (element instanceof IResource) {
			if (element instanceof IProject)
				return PHPWorkspaceModelManager.getInstance();
			final IResource parent = ((IResource) element).getParent();
			if (parent instanceof IProject) {
				//				return PHPWorkspaceModelManager.getInstance().getModelForProject((IProject) parent);
			}

			return parent;
		} else if (element instanceof PHPCodeData) {
			final PHPCodeData parent = ((PHPCodeData) element).getContainer();
			// for source folders that are contained in a project source folder
			// we have to skip the source folder root as the parent.

			if (parent == null && element instanceof PHPFileData) {
				final IResource resource = getResource(element);
				if (resource == null) {
					return parent;
				}
				final IResource parentResource = resource.getParent();
				if (parentResource.exists()) {
					return parentResource;
				}
			}
			return parent;
		} else if (element instanceof PHPProjectModel)
			return PHPWorkspaceModelManager.getInstance();

		return null;
	}

	public static PHPFileData getPHPFile(final IFile file) {
		final PHPProjectModel projectModel = PHPWorkspaceModelManager.getInstance().getModelForProject(file.getProject());
		if (projectModel != null) {
			final PHPFileData fileData = projectModel.getFileData(file.getFullPath().toString());
			if (fileData != null)
				return fileData;
		}
		return null;
	}

	public static PHPFileData getPHPFileContainer(final PHPCodeData element) {
		if (element instanceof PHPFileData)
			return (PHPFileData) element;
		PHPCodeData parent = element.getContainer();
		while (parent != null && !(parent instanceof PHPFileData))
			parent = parent.getContainer();
		if (parent == null) {
			final UserData userData = element.getUserData();
			if (userData != null) {
				final IPath path = new Path(userData.getFileName());
				parent = PHPWorkspaceModelManager.getInstance().getModelForFile(path.toString());
			}
		}
		return (PHPFileData) parent;
	}

	public static IContainer getPHPFolderRoot(final PHPCodeData element) {
		final IResource resource = getResource(element);
		return resource != null ? resource.getProject() : null;

	}

	public static IResource getResource(final Object element) {
		if (element instanceof PHPCodeData) {
			PHPCodeData parent = null;
			if (!(element instanceof PHPFileData))
				parent = getPHPFileContainer((PHPCodeData) element);
			final PHPFileData fileData = (PHPFileData) parent;
			String filename = null;
			final PHPCodeData codeData = (PHPCodeData) element;
			if (fileData != null)
				filename = fileData.getName();
			else if (codeData.getUserData() != null) {
				final UserData userData = codeData.getUserData();
				filename = userData.getFileName();
			} else
				return null; //no resource
			final Path path = new Path(filename);
			if (path.segmentCount() < 2) // path doesnt include project name, return null
				return null;
			IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (resource == null && ExternalFilesRegistry.getInstance().isEntryExist(new Path(filename).toOSString())) {
				resource = ExternalFilesRegistry.getInstance().getFileEntry(new Path(filename).toOSString());
			}
			return resource;
		} else if (element instanceof PHPProjectModel) {
			final PHPProjectModel projectModel = (PHPProjectModel) element;
			IProject project = projectModel.getProject();
			if (project == null) {
				project = PHPWorkspaceModelManager.getInstance().getProjectForModel(projectModel);
			}
			return project;
		} else if (element instanceof IResource)
			return (IResource) element;
		return null;
	}

	public static PHPClassData getSuperClass(final PHPClassData classData) {
		final PHPSuperClassNameData superClassNameData = classData.getSuperClassData();
		if (superClassNameData == null)
			return null;
		final String superClassName = superClassNameData.getName();
		if (superClassName == null)
			return null;
		final PHPFileData fileData = getPHPFileContainer(classData);
		String fileName = null;
		if (fileData != null)
			fileName = fileData.getName();
		if (fileName != null && fileName.equals("")) //$NON-NLS-1$
			fileName = null;
		PHPClassData superClassData = null;
		IPhpModel model = null;
		IProject project = null;
		// first try to find in the same user model:
		final IResource resource = getResource(classData);
		if (resource != null) {
			project = resource.getProject();
			model = PHPWorkspaceModelManager.getInstance().getModelForProject(project);
			if (model != null) {
				superClassData = model.getClass(resource.getName(), superClassName);
				if (superClassData != null)
					return superClassData;
			}
		}
		// then look up in other models by file:
		final PHPProjectModel[] projectModels = PHPWorkspaceModelManager.getInstance().listModels();
		for (PHPProjectModel projectModel : projectModels)
			if (projectModel.getFileData(fileName) == fileData && (superClassData = projectModel.getClass(fileName, superClassName)) != null)
				return superClassData;
		// an then just all the models:
		for (PHPProjectModel projectModel : projectModels)
			if ((superClassData = projectModel.getClass(fileName, superClassName)) != null)
				return superClassData;
		return null;
	}

	/**
	 * This method returns all the interface that the given class data implements.
	 * Note : This method returns only the interfaces 1 level above. (Not recursively)
	 * @param classData
	 * @return the interfaces
	 */
	public static PHPClassData[] getInterfaces(final PHPClassData classData) {
		final PHPInterfaceNameData[] interfaceNameDatas = classData.getInterfacesNamesData();
		int numOfInterfaces = interfaceNameDatas.length;
		ArrayList interfacesList = new ArrayList();
		for (int i = 0; i < numOfInterfaces; i++) {
			if (interfaceNameDatas[i] == null) {
				continue;
			}
			final String interfaceName = interfaceNameDatas[i].getName();
			if (interfaceName == null) {
				continue;
			}
			final PHPFileData fileData = getPHPFileContainer(classData);
			String fileName = null;
			if (fileData != null) {
				fileName = fileData.getName();
			}
			if ("".equals(fileName)) {
				fileName = null;
			}
			PHPClassData interfaceClassData = null;
			// first try to find in the same user model:
			final IResource resource = getResource(classData);
			if (resource != null) {
				final IProject project = resource.getProject();
				final IPhpModel model = PHPWorkspaceModelManager.getInstance().getModelForProject(project);
				if (model != null) {
					interfaceClassData = model.getClass(resource.getName(), interfaceName);
					if (interfaceClassData != null) {
						interfacesList.add(interfaceClassData);
					}
				}
			} else {
				// then look up in other models by file:
				final PHPProjectModel[] projectModels = PHPWorkspaceModelManager.getInstance().listModels();
				boolean foundInModeByFile = false;
				for (PHPProjectModel projectModel : projectModels) {
					if (projectModel.getFileData(fileName) == fileData && (interfaceClassData = projectModel.getClass(fileName, interfaceName)) != null) {
						interfacesList.add(interfaceClassData);
						foundInModeByFile = true;
						break;
					}
				}
				if (foundInModeByFile) {
					continue;
				}
				// an then just all the models:
				for (PHPProjectModel projectModel : projectModels) {
					if ((interfaceClassData = projectModel.getClass(fileName, interfaceName)) != null) {
						interfacesList.add(interfaceClassData);
					}
				}
			}
		}
		PHPClassData[] interfacesResult = new PHPClassData[interfacesList.size()];
		interfacesList.toArray(interfacesResult);
		return interfacesResult;
	}

	public static PHPProjectModel getProjectModelForFile(PHPFileData fileData) {
		PHPProjectModel model = null;
		IProject project = (IProject) getPHPFolderRoot(fileData);
		if (project != null) {
			model = PHPWorkspaceModelManager.getInstance().getModelForProject(project);
		}
		if (model == null) {
			IPhpModel[] models = PHPWorkspaceModelManager.getInstance().listModels();
			for (int i = 0; i < models.length; ++i) {
				if (models[i].getFileData(fileData.getName()) == fileData) {
					model = (PHPProjectModel) models[i];
					break;
				}
			}
		}
		return model;
	}

	public static IPhpModel getIncludeModelForFile(PHPProjectModel model, PHPFileData fileData) {
		PHPIncludePathModelManager includeManager = (PHPIncludePathModelManager) model.getModel(PHPIncludePathModelManager.COMPOSITE_INCLUDE_PATH_MODEL_ID);
		if (includeManager == null)
			return null;
		IPhpModel[] includeModels = includeManager.listModels();
		for (int j = 0; j < includeModels.length; ++j) {
			if (includeModels[j].getFileData(fileData.getName()) == fileData) {
				return includeModels[j];
			}
		}
		return null;
	}

	public static boolean hasChildren(final PHPCodeData element) {
		if (element instanceof PHPFunctionData)
			return false;
		else if (element instanceof PHPClassData) {
			final PHPClassData classData = (PHPClassData) element;
			return classData.getFunctions().length > 0 || classData.getVars().length > 0 || classData.getConsts().length > 0;
		} else if (element instanceof PHPVariableData || element instanceof PHPKeywordData || element instanceof PHPConstantData || element instanceof PHPClassConstData || element instanceof PHPIncludeFileData)
			return false;
		return true;

	}

	public static boolean hasPhpExtention(final IFile file) {
		final String fileName = file.getName();
		final int index = fileName.lastIndexOf('.');
		if (index == -1) {
			return false;
		}
		String extension = fileName.substring(index + 1);

		// handle SVN external file extension (e.g sample.php:12358)
		// fixed bug 186064
		if (file instanceof ExternalFileWrapper) {
			int position = extension.indexOf(':');
			extension = position > 0 ? extension.substring(0, position) : extension;
		}

		final IContentType type = Platform.getContentTypeManager().getContentType(ContentTypeIdForPHP.ContentTypeID_PHP);
		final String[] validExtensions = type.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
		for (String validExtension : validExtensions) {
			if (extension.equalsIgnoreCase(validExtension)) {
				return true;
			}
		}

		return false;
	}

	public static boolean hasSuperClass(final PHPClassData classData, final PHPClassData superClassData) {
		final PHPSuperClassNameData currentSuperClassNameData = classData.getSuperClassData();
		if (currentSuperClassNameData == null)
			return false;
		final String currentSuperClassName = currentSuperClassNameData.getName();
		if (currentSuperClassName == null)
			return false;
		PHPClassData currentSuperClassData = classData;
		while ((currentSuperClassData = getSuperClass(currentSuperClassData)) != null)
			if (currentSuperClassData == superClassData)
				return true;
		return false;
	}

	/**
	 * recursively checks if the given class extends the superclass
	 * Currently only can detect for files that are inside the project
	 *
	 * @param classData
	 * @param superClassName
	 * @return <code>true</code> if the class extends other class
	 */
	public static boolean hasSuperClass(final PHPClassData classData, final String superClassName) {
		return discoverSuperClass(classData, superClassName) != null || superClassName.equalsIgnoreCase(getSuperClassName(classData));
	}

	public static String getSuperClassName(PHPClassData classData) {
		final PHPSuperClassNameData currentSuperClassNameData = classData.getSuperClassData();
		if (currentSuperClassNameData != null)
			return currentSuperClassNameData.getName();
		return null;
	}

	public static PHPClassData discoverSuperClass(final PHPClassData classData, final String superClassName) {
		String currentSuperClassName = getSuperClassName(classData);
		if (currentSuperClassName == null)
			return null;
		PHPClassData currentSuperClassData = classData;
		Set<PHPClassData> visitedClasses = new HashSet();
		visitedClasses.add(currentSuperClassData);
		while ((currentSuperClassData = getSuperClass(currentSuperClassData)) != null) {
			if (visitedClasses.contains(currentSuperClassData))
				return null;
			if ((currentSuperClassName = currentSuperClassData.getName()) == null)
				return null;
			if (currentSuperClassName.compareToIgnoreCase(superClassName) == 0)
				return currentSuperClassData;
			visitedClasses.add(currentSuperClassData);
		}
		return null;
	}

	public static PHPClassData discoverInterface(final PHPClassData classData, final String interfaceName) {
		PHPClassData[] interfaces = getInterfaces(classData);
		for (int i = 0; i < interfaces.length; ++i) {
			if (interfaces[i].getName() != null && interfaces[i].getName().equalsIgnoreCase(interfaceName)) {
				return interfaces[i];
			}
		}
		return null;
	}

	public static boolean isExternal(final Object target) {
		return false;
	}

	public static boolean isPhpFile(final IFile file) {
		IContentDescription contentDescription = null;
		if (!file.exists()) {
			return hasPhpExtention(file);
		}
		try {
			contentDescription = file.getContentDescription();
		} catch (final CoreException e) {
			return hasPhpExtention(file);
		}

		if (contentDescription == null) {
			return hasPhpExtention(file);
		}

		return ContentTypeIdForPHP.ContentTypeID_PHP.equals(contentDescription.getContentType().getId());
	}

	public static boolean isReadOnly(final Object target) {
		return false;
	}

	public static String getRelativeLocation(IPhpModel model, String location) {
		PHPFileData fileData = model.getFileData(location);

		if (fileData != null) {
			// deterministic
			IResource resource = getResource(fileData);
			if (resource != null && resource.exists()) { // file is in a project
				IProject fileProject = resource.getProject();
				if (fileProject.isAccessible()) {
					return new Path(location).removeFirstSegments(1).toString();
				}
			} else if (model instanceof PHPProjectModel) { // file is in an include file
				IPhpModel includeModel = getIncludeModelForFile((PHPProjectModel) model, fileData);
				IPath path = getIncludeModelLocation(includeModel);
				if (includeModel.getFileData(location) == fileData) {
					return new Path(location).setDevice("").removeFirstSegments(path.segmentCount()).toString(); //$NON-NLS-1$
				}
			}
		} else {
			// heuristic
			IPath pathLocation = new Path(location);
			if (model instanceof PHPProjectModel) {
				String projectName = pathLocation.segment(0);
				if (projectName.equals(((PHPProjectModel) model).getProject().getName()))
					return pathLocation.removeFirstSegments(1).makeRelative().toString();
				PHPIncludePathModelManager includeManager = (PHPIncludePathModelManager) ((PHPProjectModel) model).getModel(PHPIncludePathModelManager.COMPOSITE_INCLUDE_PATH_MODEL_ID);
				if (includeManager.getModel(projectName) != null)
					return pathLocation.removeFirstSegments(1).makeRelative().toString();
				if (ResourcesPlugin.getWorkspace().getRoot().getProject(projectName) != null)
					return pathLocation.removeFirstSegments(1).makeRelative().toString();
				// TODO include variables/directories
			}
		}
		return location;

	}

	public static String getRelativeLocation(IProject project, String location) {
		PHPProjectModel model = PHPWorkspaceModelManager.getInstance().getModelForProject(project);
		if (model == null) {
			return location;
		}
		return getRelativeLocation(model, location);
	}

	public static PHPFileData getFileData(Object element) {
		PHPFileData fileData = null;
		if (element instanceof IFile) {
			IFile file = (IFile) element;
			if (!isPhpFile(file)) {
				return null;
			}
			fileData = PHPWorkspaceModelManager.getInstance().getModelForFile(file.getFullPath().toString());

		}
		if (element instanceof PHPFileData) {
			fileData = (PHPFileData) element;
		}
		return fileData;
	}

	/**
	 * This method creates an ArrayList of all the functions to override from the given class.
	 * The method runs recursively on the hierarchy tree until the given class has no parents to extend/interfaces to implement.
	 * In addition , the method populates the given required PHP files list during its execution.
	 * @param project - The origin project
	 * @param classData - The given class/interface
	 * @param requiredToAdd - The required php files to be added
	 * @return The list of all the functions to be overriden (abstract or interface's functions)
	 */
	public static ArrayList<PHPFunctionData> getFunctionsToOverride(IProject project, PHPClassData classData, ArrayList<String> overridenMethodsNamesList, ArrayList<String> existingRequiredNamesList, ArrayList<String> requiredToAdd) {
		ArrayList<PHPFunctionData> temp = new ArrayList<PHPFunctionData>();
		if (classData != null) {
			PHPFunctionData[] functions = classData.getFunctions();
			if (classData.getUserData() != null) {//add required files, check if not null since PHP Language file will give NULL
				String phpFileName = classData.getUserData().getFileName();
				phpFileName = PHPModelUtil.getRelativeLocation(project, phpFileName);
				if (!existingRequiredNamesList.contains(phpFileName)) {
					existingRequiredNamesList.add(phpFileName);
					requiredToAdd.add(phpFileName);
				}
			}
			int numOfFunctions = functions.length;
			//an interface
			if (PHPModifier.isInterface(classData.getModifiers())) {
				for (int i = 0; i < numOfFunctions; i++) {
					if (!overridenMethodsNamesList.contains(functions[i].getName())) {
						temp.add(functions[i]);
						overridenMethodsNamesList.add(functions[i].getName());
					}
				}
			}
			//an abstract class
			else if (PHPModifier.isAbstract(classData.getModifiers())) {
				for (int i = 0; i < numOfFunctions; i++) {
					if (!PHPModifier.isAbstract(functions[i].getModifiers())) {
						overridenMethodsNamesList.add(functions[i].getName());
					}

					else if (!overridenMethodsNamesList.contains(functions[i].getName())) {
						temp.add(functions[i]);
						overridenMethodsNamesList.add(functions[i].getName());
					}
				}
			}

			else {//add existing methods to exclude list
				for (int i = 0; i < numOfFunctions; i++) {
					if (!overridenMethodsNamesList.contains(functions[i].getName())) {
						overridenMethodsNamesList.add(functions[i].getName());
					}
				}
			}
			
			//this class has a superclass
			if (classData.getSuperClassData() != null && classData.getSuperClassData().getName() != null) {
				PHPClassData superClass = PHPModelUtil.getSuperClass(classData);
				ArrayList<PHPFunctionData> superClassMethodsList = getFunctionsToOverride(project, superClass, overridenMethodsNamesList, existingRequiredNamesList, requiredToAdd);
				temp.addAll(superClassMethodsList);
				Iterator<PHPFunctionData> iter = superClassMethodsList.iterator();
				while (iter.hasNext()) {
					temp.add(iter.next());
				}
			}
			//this class has interfaces
			if (classData.getInterfacesNamesData() != null && classData.getInterfacesNamesData().length > 0) {
				PHPClassData[] interfaces = PHPModelUtil.getInterfaces(classData);
				int numOfInterfaces = interfaces.length;
				for (int i = 0; i < numOfInterfaces; i++) {
					ArrayList<PHPFunctionData> interfaceMethodsList = getFunctionsToOverride(project, interfaces[i], overridenMethodsNamesList, existingRequiredNamesList, requiredToAdd);
					temp.addAll(interfaceMethodsList);
				}
			}
		}
		return temp;
	}

	public static IPath getIncludeModelLocation(IPhpModel model) {
		if (model instanceof PHPIncludePathModel) {
			PHPIncludePathModel includeModel = (PHPIncludePathModel) model;
			if (includeModel.getType() == IncludePathModelType.VARIABLE) {
				return IncludePathVariableManager.instance().getIncludePathVariable(model.getID());
			}
		}
		String id = model.getID();
		if (id != null)
			return new Path(id);
		return null;
	}

	public static IPath getFileDataShortPath(PHPProjectModel model, PHPFileData fileData) {
		IPath shortPath;
		IPhpModel includeModel = getIncludeModelForFile(model, fileData);
		if (includeModel != null) {
			IPath path = getIncludeModelLocation(includeModel);
			shortPath = new Path(includeModel.getID()).append(new Path(fileData.getName()).removeFirstSegments(path.segmentCount()));
		} else {
			shortPath = new Path(fileData.getName());
		}
		return shortPath;
	}
}
