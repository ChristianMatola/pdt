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
package org.eclipse.php.internal.core.project;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.php.internal.core.PHPCorePlugin;
import org.eclipse.php.internal.core.project.options.PHPProjectOptions;

public class PHPNature implements IProjectNature {
	public static final String ID = PHPCorePlugin.ID + ".PHPNature"; //$NON-NLS-1$

	public static final String PROJECTTYPE_VALUE = "PHP"; //$NON-NLS-1$
	public static final String VALIDATION_BUILDER_ID = PHPCorePlugin.ID + ".ValidationManagerWrapper";

	private static int instanceCount = 0;
	public int instanceStamp;

	//protected WebSettings fWebSettings;

	protected IProject project;
	protected PHPProjectOptions options;

	//	private static final String LINKS_BUILDER_ID = "com.ibm.etools.webtools.additions.linksbuilder"; //$NON-NLS-1$

	public static int getInstanceCount() {
		return instanceCount;
	}

	/**
	 * WebNatureRuntime constructor comment.
	 */
	public PHPNature() {
		super();
		++instanceCount;
		instanceStamp = instanceCount;
	}

	/**
	 * Adds a builder to the build spec for the given project.
	 */
	protected ICommand addToFrontOfBuildSpec(String builderID) throws CoreException {
		ICommand command = null;
		IProjectDescription description = getProject().getDescription();
		ICommand[] commands = description.getBuildSpec();
		boolean found = false;
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(builderID)) {
				found = true;
				command = commands[i];
				break;
			}
		}
		if (!found) {
			command = description.newCommand();
			command.setBuilderName(builderID);
			ICommand[] newCommands = new ICommand[commands.length + 1];
			System.arraycopy(commands, 0, newCommands, 1, commands.length);
			newCommands[0] = command;
			IProjectDescription desc = getProject().getDescription();
			desc.setBuildSpec(newCommands);
			getProject().setDescription(desc, null);
		}
		return command;
	}

	/**
	 * Create a default file for the user given the name (directory relative to the project) and the
	 * default contents for the file.
	 * 
	 * @param newFilePath -
	 *            IPath
	 * @param newFileContents -
	 *            String
	 */
	public void createFile(IPath newFilePath, String newFileContents) throws CoreException {

		IPath projectPath = project.getFullPath();
		IWorkspace workspace = ResourcesPlugin.getWorkspace();

		createFolder(newFilePath.removeLastSegments(1).toString());

		IFile outputFile = workspace.getRoot().getFile(projectPath.append(newFilePath));
		outputFile.refreshLocal(IResource.DEPTH_INFINITE, null);

		InputStream inputStream = new ByteArrayInputStream(newFileContents.getBytes());
		if (!(outputFile.exists()))
			outputFile.create(inputStream, true, null);
	}

	/**
	 * Removes this nature from the project.
	 * 
	 * @see IProjectNature#deconfigure
	 */
	public void deconfigure() throws CoreException {
		removeFromBuildSpec(PHPProjectOptions.BUILDER_ID);
		removeFromBuildSpec(VALIDATION_BUILDER_ID);
		clean();
	}

	private void clean() {
		options = null;
		project = null;
	}

	/**
	 * Insert the method's description here. Creation date: (11/1/2001 2:25:22 PM)
	 * 
	 * @param builderID
	 *            java.lang.String
	 * @exception org.eclipse.core.runtime.CoreException
	 *                The exception description.
	 */
	protected void removeFromBuildSpec(String builderID) throws org.eclipse.core.runtime.CoreException {
		IProjectDescription description = getProject().getDescription();
		ICommand[] commands = description.getBuildSpec();
		boolean found = false;
		for (int i = 0; i < commands.length; ++i) {
			if (commands[i].getBuilderName().equals(builderID)) {
				found = true;
				break;
			}
		}
		if (!found) {
			ICommand command = description.newCommand();
			command.setBuilderName(builderID);
			ICommand[] newCommands = new ICommand[commands.length + 1];
			System.arraycopy(commands, 0, newCommands, 1, commands.length);
			newCommands[0] = command;
			IProjectDescription desc = getProject().getDescription();
			desc.setBuildSpec(newCommands);
			getProject().setDescription(desc, null);
		}

	}

	/**
	 * Configures the project with this nature. This is called by <code>IProject.addNature</code>
	 * and should not be called directly by clients. The nature extension id is added to the list of
	 * natures on the project by <code>IProject.addNature</code>, and need not be added here.
	 * 
	 * All subtypes must call super.
	 * 
	 * @exception CoreException
	 *                if this method fails.
	 */
	public void configure() throws org.eclipse.core.runtime.CoreException {
		// enable workspace validation for this nature
		addToFrontOfBuildSpec(VALIDATION_BUILDER_ID);
		addToFrontOfBuildSpec(PHPProjectOptions.BUILDER_ID);
	}

	/**
	 * Returns the project to which this project nature applies.
	 * 
	 * @return the project handle
	 */
	public org.eclipse.core.resources.IProject getProject() {
		return project;
	}

	/**
	 * Sets the project to which this nature applies. Used when instantiating this project nature
	 * runtime. This is called by <code>IProject.addNature</code> and should not be called
	 * directly by clients.
	 * 
	 * @param project
	 *            the project to which this nature applies
	 */
	public void setProject(org.eclipse.core.resources.IProject newProject) {
		clean();
		project = newProject;
	}

	/**
	 * Create a folder relative to the project based on aProjectRelativePathString.
	 * 
	 * @exception com.ibm.itp.core.api.resources.CoreException
	 *                The exception description.
	 */
	public IFolder createFolder(String aProjectRelativePathString) throws CoreException {
		if (aProjectRelativePathString != null && aProjectRelativePathString.length() > 0)
			return createFolder(new Path(aProjectRelativePathString));
		return null;
	}

	/**
	 * Create a folder relative to the project based on aProjectRelativePathString.
	 * 
	 * @exception com.ibm.itp.core.api.resources.CoreException
	 *                The exception description.
	 */
	public IFolder createFolder(IPath aProjectRelativePath) throws CoreException {
		if (aProjectRelativePath != null && !aProjectRelativePath.isEmpty()) {
			IFolder folder = getWorkspace().getRoot().getFolder(getProjectPath().append(aProjectRelativePath));
			if (!folder.exists())
				folder.create(true, true, null);
			return folder;
		}
		return null;
	}

	/**
	 * Adds a nauture to a project
	 */
	//	protected static void addNatureToProject(IProject proj, String natureId) throws CoreException {
	//		ProjectUtilities.addNatureToProject(proj, natureId);
	//	}
	/**
	 * Return the full path of the project.
	 */
	protected IPath getProjectPath() {
		return getProject().getFullPath();
	}

	public IWorkspace getWorkspace() {
		return getProject().getWorkspace();
	}

	public PHPProjectOptions getOptions() {
		if (options == null) {
			options = new PHPProjectOptions(project);
			//			boolean useProjectSpecificSettings = new ProjectScope(project).getNode(PhpOptionsPreferenceKeys.PHP_OPTION_NODE).getBoolean(PhpOptionsPreferenceKeys.PHP_OPTIONS_PER_PROJECT, false);
			//			if (!useProjectSpecificSettings) {
			//				PhpVerionsProjectOptionAdapter.setVersion(options, CorePreferenceConstants.getPreferenceStore().getString(CorePreferenceConstants.Keys.PHP_VERSION));
			//			}
			//			IEclipsePreferences projectProperties = new ProjectScope(project).getNode(PhpOptionsPreferenceKeys.PHP_OPTION_NODE);
			//			projectProperties.put
			//			projectProperties
		}
		return options;
	}

}