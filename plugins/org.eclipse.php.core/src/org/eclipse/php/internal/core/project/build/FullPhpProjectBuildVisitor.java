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
package org.eclipse.php.internal.core.project.build;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.php.internal.core.CoreMessages;
import org.eclipse.php.internal.core.documentModel.markers.MarkerContributor;
import org.eclipse.php.internal.core.phpModel.PHPModelUtil;
import org.eclipse.php.internal.core.phpModel.parser.PHPWorkspaceModelManager;
import org.eclipse.php.internal.core.project.options.PHPProjectOptions;

public class FullPhpProjectBuildVisitor implements IResourceVisitor {

	private IProgressMonitor monitor;
	private MarkerContributor validator = MarkerContributor.getInstance();

	public FullPhpProjectBuildVisitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	public boolean visit(IResource resource) {
		if(monitor.isCanceled()){
			return false;
		}
		// parse each PHP file with the parserFacade which adds it to
		// the model
		if (resource.getType() == IResource.FILE) {
			handle((IFile) resource);
			return false;
		}

		if (resource.getType() == IResource.PROJECT) {
			return handle((IProject) resource);
		}

		return true;
	}

	private boolean handle(IProject project) {
		//check if the project contains PHP
		if (PHPWorkspaceModelManager.getInstance().getModelForProject(project, true) == null) {
			return false;
		}
		PHPProjectOptions projectOptions = PHPProjectOptions.forProject(project);
		projectOptions.validateIncludePath();
		return true;
	}

	private void handle(IFile file) {
		if (monitor.isCanceled()) {
			return;
		}
		if (!PHPModelUtil.isPhpFile(file)) {
			monitor.worked(1);
			return;
		}
		monitor.subTask(NLS.bind(CoreMessages.getString("FullPhpProjectBuildVisitor_0"), file.getFullPath().toPortableString()));

		PHPWorkspaceModelManager.getInstance().addFileToModel(file);		
		validator.markFile(file);
		monitor.worked(1);

	}
}
