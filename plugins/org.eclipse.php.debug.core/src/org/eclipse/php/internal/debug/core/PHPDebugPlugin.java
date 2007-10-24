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
package org.eclipse.php.internal.debug.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.php.internal.debug.core.debugger.AbstractDebuggerConfiguration;
import org.eclipse.php.internal.debug.core.launching.XDebugLaunchListener;
import org.eclipse.php.internal.debug.core.preferences.PHPDebugCorePreferenceNames;
import org.eclipse.php.internal.debug.core.preferences.PHPDebuggersRegistry;
import org.eclipse.php.internal.debug.core.xdebug.XDebugPreferenceInit;
import org.eclipse.php.internal.debug.daemon.DaemonPlugin;
import org.eclipse.php.internal.server.core.Server;
import org.eclipse.php.internal.server.core.manager.ServersManager;
import org.osgi.framework.BundleContext;

/**
 * The main plugin class to be used in the desktop.
 */
public class PHPDebugPlugin extends Plugin {

	public static final String ID = "org.eclipse.php.debug.core"; //$NON-NLS-1$
	public static final int INTERNAL_ERROR = 10001;

	//The shared instance.
	private static PHPDebugPlugin plugin;
	private static final String BASE_URL = "http://localhost";
	private static String fPHPDebugPerspective = "org.eclipse.php.debug.ui.PHPDebugPerspective";
	private static String fDebugPerspective = "org.eclipse.debug.ui.DebugPerspective";
	private static boolean fIsSupportingMultipleDebugAllPages = true;
	private boolean fInitialAutoRemoveLaunches;
	private static boolean fLaunchChangedAutoRemoveLaunches;

	/**
	 * The constructor.
	 */
	public PHPDebugPlugin() {
		plugin = this;
	}

	public static final boolean DebugPHP;

	static {
		String value = Platform.getDebugOption("org.eclipse.php.debug.core/debug"); //$NON-NLS-1$
		DebugPHP = value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}

	/**
	 * This method is called upon plug-in activation
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		// Set the AutoRemoveOldLaunchesListener
		IPreferenceStore preferenceStore = DebugUIPlugin.getDefault().getPreferenceStore();
		fInitialAutoRemoveLaunches = preferenceStore.getBoolean(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES);
		preferenceStore.addPropertyChangeListener(new AutoRemoveOldLaunchesListener());
		org.eclipse.php.internal.server.core.Activator.getDefault(); // TODO - Check if getInstance is needed
		// check for default server
		createDefaultPHPServer();

		// TODO - XDebug - See if this can be removed and use a preferences initializer.
		// It's important the the default setting will occur before loading the daemons.
		XDebugPreferenceInit.setDefaults();

		// Start all the daemons
		DaemonPlugin.getDefault().startDaemons(null);

		// TODO - XDebug - See if this can be removed
		XDebugLaunchListener.getInstance();
	}

	/**
	 * This method is called when the plug-in is stopped
	 */
	public void stop(BundleContext context) throws Exception {
		XDebugLaunchListener.shutdown();
		savePluginPreferences();

		super.stop(context);
		plugin = null;
		DebugUIPlugin.getDefault().getPreferenceStore().setValue(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES, fInitialAutoRemoveLaunches);
	}

	/**
	 * Returns the shared instance.
	 */
	public static PHPDebugPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the PHP debug ID.
	 */
	public static String getID() {
		return IPHPConstants.ID_PHP_DEBUG_CORE;
	}

	public static boolean getStopAtFirstLine() {
		Preferences prefs = getDefault().getPluginPreferences();
		return prefs.getBoolean(PHPDebugCorePreferenceNames.STOP_AT_FIRST_LINE);

	}

	public static boolean getDebugInfoOption() {
		Preferences prefs = getDefault().getPluginPreferences();
		return prefs.getBoolean(PHPDebugCorePreferenceNames.RUN_WITH_DEBUG_INFO);

	}

	public static boolean getOpenInBrowserOption() {
		Preferences prefs = getDefault().getPluginPreferences();
		return prefs.getBoolean(PHPDebugCorePreferenceNames.OPEN_IN_BROWSER);
	}

	/**
	 * Returns the debugger id that is currently in use.
	 *
	 * @return The debugger id that is in use.
	 * @since PDT 1.0
	 */
	public static String getCurrentDebuggerId() {
		Preferences prefs = getDefault().getPluginPreferences();
		return prefs.getString(PHPDebugCorePreferenceNames.PHP_DEBUGGER_ID);
	}

	/**
	 * Returns true if the auto-save is on for any dirty file that exists when a Run/Debug launch is triggered.
	 *
	 * @deprecated since PDT 1.0, this method simply extracts the value of IInternalDebugUIConstants.PREF_SAVE_DIRTY_EDITORS_BEFORE_LAUNCH
	 * from the {@link DebugUIPlugin}
	 */
	public static boolean getAutoSaveDirtyOption() {
		String saveDirty = DebugUIPlugin.getDefault().getPreferenceStore().getString(IInternalDebugUIConstants.PREF_SAVE_DIRTY_EDITORS_BEFORE_LAUNCH);
		if (saveDirty == null) {
			return true;
		}
		return Boolean.valueOf(saveDirty).booleanValue();
	}

	public static boolean getOpenDebugViewsOption() {
		Preferences prefs = getDefault().getPluginPreferences();
		return prefs.getBoolean(PHPDebugCorePreferenceNames.OPEN_DEBUG_VIEWS);

	}

	/**
	 * Returns the debugger port for the given debugger id.
	 * Return -1 if the debuggerId does not exist, or the debugger does not have a debug port.
	 *
	 * @param debuggerId
	 * @return The debug port, or -1.
	 */
	public static int getDebugPort(String debuggerId) {
		AbstractDebuggerConfiguration debuggerConfiguration = PHPDebuggersRegistry.getDebuggerConfiguration(debuggerId);
		if (debuggerConfiguration == null) {
			return -1;
		}
		return debuggerConfiguration.getPort();

	}

	public static String getWorkspaceDefaultServer() {
		Preferences serverPrefs = org.eclipse.php.internal.server.core.Activator.getDefault().getPluginPreferences();
		return serverPrefs.getString(ServersManager.DEFAULT_SERVER_PREFERENCES_KEY);

	}

	/**
	 * Creates a default server in case the ServersManager does not hold any defined server.
	 */
	public static void createDefaultPHPServer() {
		if (ServersManager.getServers().length == 0) {
			Server server = ServersManager.createServer(IPHPConstants.Default_Server_Name, BASE_URL);
			ServersManager.save();
			ServersManager.setDefaultServer(null, server);
		}
	}

	public static void log(IStatus status) {
		try {
			getDefault().getLog().log(status);
		} catch (Exception e) {
		}
	}

	public static void log(Throwable e) {
		log(new Status(IStatus.ERROR, ID, INTERNAL_ERROR, "PHPDebug plugin internal error", e)); //$NON-NLS-1$
	}

	public static void logErrorMessage(String message) {
		log(new Status(IStatus.ERROR, ID, INTERNAL_ERROR, message, null));
	}

	/**
	 * Returns if multiple sessions of debug launches are allowed when one of the launches
	 * contains a 'debug all pages' attribute.
	 *
	 * @return True, the multiple sessions are allowed; False, otherwise.
	 */
	public static boolean supportsMultipleDebugAllPages() {
		return fIsSupportingMultipleDebugAllPages;
	}

	/**
	 * Allow or disallow the multiple debug sessions that has a launch attribute of 'debug all pages'.
	 *
	 * @param supported
	 */
	public static void setMultipleDebugAllPages(boolean supported) {
		fIsSupportingMultipleDebugAllPages = supported;
	}

	//
	//	/**
	//	 * Returns true if the auto remove launches was disabled by a PHP launch.
	//	 * The auto remove flag is usually disabled when a PHP server launch was triggered and a
	//	 * 'debug all pages' flag was on.
	//	 * Note that this method will return true only if a php launch set it and the debug preferences has a 'true'
	//	 * value for IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES.
	//	 *
	//	 * @return True iff the auto remove old launches was disabled.
	//	 */
	//	public static boolean isDisablingAutoRemoveLaunches() {
	//		return fDisableAutoRemoveLaunches;
	//	}

	/**
	 * Enable or disable the auto remove old launches flag.
	 * The auto remove flag is usually disabled when a PHP server launch was triggered and a
	 * 'debug all pages' flag was on.
	 * Note that this method actually sets the IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES preferences key
	 * for the {@link DebugUIPlugin}.
	 *
	 * @param disableAutoRemoveLaunches
	 */
	public static void setDisableAutoRemoveLaunches(boolean disableAutoRemoveLaunches) {
		if (DebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES) == disableAutoRemoveLaunches) {
			fLaunchChangedAutoRemoveLaunches = true;
			DebugUIPlugin.getDefault().getPreferenceStore().setValue(IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES, !disableAutoRemoveLaunches);
		}
	}

	/**
	 * Returns the initial value of the auto-remove-old launches.
	 *
	 * @return
	 */
	public boolean getInitialAutoRemoveLaunches() {
		return fInitialAutoRemoveLaunches;
	}

	//
	private class AutoRemoveOldLaunchesListener implements IPropertyChangeListener {

		public void propertyChange(PropertyChangeEvent event) {
			if (IDebugUIConstants.PREF_AUTO_REMOVE_OLD_LAUNCHES.equals(event.getProperty())) {
				if (fLaunchChangedAutoRemoveLaunches) {
					fLaunchChangedAutoRemoveLaunches = false;// We got the event, so reset the flag.
				} else {
					// The event was triggered from some other source - e.g. The user changed the preferences manually.
					fInitialAutoRemoveLaunches = ((Boolean) event.getNewValue()).booleanValue();
				}
			}
		}
	}
}
