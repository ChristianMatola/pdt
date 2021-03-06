/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Zend Technologies
 *******************************************************************************/
/**
 * 
 */
package org.eclipse.php.internal.debug.core.launching;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.php.internal.core.PHPVersion;
import org.eclipse.php.internal.debug.core.IPHPDebugConstants;
import org.eclipse.php.internal.debug.core.PHPDebugPlugin;
import org.eclipse.php.internal.debug.core.PHPRuntime;
import org.eclipse.php.internal.debug.core.debugger.AbstractDebuggerConfiguration;
import org.eclipse.php.internal.debug.core.preferences.PHPDebugCorePreferenceNames;
import org.eclipse.php.internal.debug.core.preferences.PHPDebuggersRegistry;
import org.eclipse.php.internal.debug.core.preferences.PHPexeItem;
import org.eclipse.php.internal.debug.core.preferences.PHPexes;

/**
 * The PHP launch delegate proxy is designed to supply flexibility in delegating
 * launches to different types of launch configuration delegates. Using the
 * proxy model can allow a runtime determination of the right delegate to
 * launch.
 * 
 * @author Shalom Gibly
 * 
 */
public class PHPLaunchDelegateProxy implements ILaunchConfigurationDelegate2 {

	protected ILaunchConfigurationDelegate2 launchConfigurationDelegate;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#buildForLaunch
	 * (org.eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean buildForLaunch(ILaunchConfiguration configuration,
			String mode, IProgressMonitor monitor) throws CoreException {
		return getConfigurationDelegate(configuration).buildForLaunch(
				configuration, mode, monitor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#finalLaunchCheck
	 * (org.eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean finalLaunchCheck(ILaunchConfiguration configuration,
			String mode, IProgressMonitor monitor) throws CoreException {
		return getConfigurationDelegate(configuration).finalLaunchCheck(
				configuration, mode, monitor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#getLaunch(
	 * org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	public ILaunch getLaunch(ILaunchConfiguration configuration, String mode)
			throws CoreException {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType exeType = lm
				.getLaunchConfigurationType(IPHPDebugConstants.PHPEXELaunchType);
		if (configuration.getType().equals(exeType)) {
			configuration = updatePHPExeAttributes(configuration);
		}
		return getConfigurationDelegate(configuration).getLaunch(configuration,
				mode);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#preLaunchCheck
	 * (org.eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean preLaunchCheck(ILaunchConfiguration configuration,
			String mode, IProgressMonitor monitor) throws CoreException {
		return getConfigurationDelegate(configuration).preLaunchCheck(
				configuration, mode, monitor);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.
	 * eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.debug.core.ILaunch,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		// Launch
		try {
			getConfigurationDelegate(configuration).launch(configuration, mode,
					launch, monitor);
		} finally {
			// Clear the launch configuration delegate.
			launchConfigurationDelegate = null;
		}
	}

	/**
	 * Create and return a launch configuration delegate. In case the delegate
	 * was already created, return the cached delegate. Note that in order to
	 * allow class instanciation from non-dependent plug-in, there is a need to
	 * define the plug-in as Eclipse-RegisterBuddy: org.eclipse.php.debug.core
	 * 
	 * @param configuration
	 *            An {@link ILaunchConfiguration}
	 */
	protected ILaunchConfigurationDelegate2 getConfigurationDelegate(
			ILaunchConfiguration configuration) throws CoreException {
		String className = configuration.getAttribute(
				PHPDebugCorePreferenceNames.CONFIGURATION_DELEGATE_CLASS, ""); //$NON-NLS-1$
		if (className.length() == 0) {
			throw new IllegalArgumentException();
		}
		if (launchConfigurationDelegate == null
				|| !launchConfigurationDelegate.getClass().getCanonicalName()
						.equals(className)) {
			try {
				launchConfigurationDelegate = (ILaunchConfigurationDelegate2) Class
						.forName(className).newInstance();
			} catch (Throwable t) {
				throw new CoreException(new Status(IStatus.ERROR,
						PHPDebugPlugin.ID, 0,
						"Launch configuration delegate loading error.", t)); //$NON-NLS-1$
			}
		}
		return launchConfigurationDelegate;
	}

	private ILaunchConfiguration updatePHPExeAttributes(
			ILaunchConfiguration configuration) throws CoreException {
		PHPexeItem item = null;
		String path = configuration.getAttribute(PHPRuntime.PHP_CONTAINER,
				(String) null);
		if (path == null) {
			IProject project = null;
			IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace()
					.getRoot();
			String projectName = configuration.getAttribute(
					IPHPDebugConstants.PHP_Project, (String) null);
			if (projectName != null) {
				project = workspaceRoot.getProject(projectName);
			} else {
				String phpScriptString = configuration.getAttribute(
						IPHPDebugConstants.ATTR_FILE, (String) null);
				IPath filePath = new Path(phpScriptString);
				IResource scriptRes = workspaceRoot.findMember(filePath);
				if (scriptRes != null) {
					project = scriptRes.getProject();
				}
			}
			item = PHPDebugPlugin.getPHPexeItem(project);
		} else {
			IPath exePath = Path.fromPortableString(path);
			PHPVersion version = PHPRuntime.getPHPVersion(exePath);
			if (version == null) {
				String exeName = exePath.lastSegment();
				item = PHPexes.getInstance().getItem(exeName);
			} else {
				item = PHPDebugPlugin.getPHPexeItem(version);
			}
		}
		if (item != null) {
			ILaunchConfigurationWorkingCopy wc = configuration.getWorkingCopy();
			wc.setAttribute(IPHPDebugConstants.ATTR_EXECUTABLE_LOCATION, item
					.getExecutable().toString());
			String debuggerId = item.getDebuggerID();
			wc.setAttribute(PHPDebugCorePreferenceNames.PHP_DEBUGGER_ID,
					debuggerId);
			AbstractDebuggerConfiguration debuggerConfiguration = PHPDebuggersRegistry
					.getDebuggerConfiguration(debuggerId);
			wc.setAttribute(
					PHPDebugCorePreferenceNames.CONFIGURATION_DELEGATE_CLASS,
					debuggerConfiguration.getScriptLaunchDelegateClass());
			if (item.getINILocation() != null) {
				wc.setAttribute(IPHPDebugConstants.ATTR_INI_LOCATION, item
						.getINILocation().toString());
			} else {
				wc.setAttribute(IPHPDebugConstants.ATTR_INI_LOCATION,
						(String) null);
			}
			configuration = wc.doSave();
		}
		return configuration;
	}

}
