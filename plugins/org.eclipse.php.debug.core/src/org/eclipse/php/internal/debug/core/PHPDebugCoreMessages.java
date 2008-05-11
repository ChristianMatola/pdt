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

import org.eclipse.osgi.util.NLS;

/**
 * Strings used by PHP Debugger Core
 *
 */
public class PHPDebugCoreMessages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.php.internal.debug.core.PHPDebugCoreMessages";//$NON-NLS-1$

    public static String DebugConnectionThread_oldDebuggerVersion;
	public static String DebugServerTestEvent_success;
	public static String DebugServerTestEvent_timeOutMessage;
	public static String LineBreakPointMessage_1;
    public static String ConditionalBreakPointMessage_1;
    public static String ConditionalBreakPointMessage_2;
    public static String DebuggerFileNotFound_1;
    public static String DebuggerDebugPortInUse_1;
    public static String DebuggerConnection_Problem_1;
    public static String DebuggerConnection_Problem_2;
    public static String DebuggerConnection_Problem_3;
    public static String DebuggerConnection_Failed_1;
    public static String Debugger_Unexpected_Error_1;
    public static String Debugger_ResourceNotFound;
    public static String Debugger_LaunchError_title;
    public static String Debugger_InvalidDebugResource;
    public static String Debugger_General_Error;
    public static String Debugger_Launch_Error;
    public static String Debugger_Error_Message;
    public static String Debugger_Error_Message_2;
    public static String Debugger_Error_Message_3;
    public static String Debugger_Error_Crash_Message;
    public static String Debugger_Incompatible_Protocol;
    public static String Port_Error_Message_Message;
    public static String Port_Error_Message_Title;

	public static String PHPLaunchUtilities_activeLaunchDetected;
	public static String PHPLaunchUtilities_confirmation;
	public static String PHPLaunchUtilities_multipleLaunchesPrompt;
	public static String PHPLaunchUtilities_phpLaunchTitle;
	public static String PHPLaunchUtilities_rememberDecision;
	public static String PHPLaunchUtilities_PHPPerspectiveSwitchTitle;
	public static String PHPLaunchUtilities_PHPPerspectiveSwitchMessage;
	public static String PHPLaunchUtilities_terminate;
	public static String PHPLaunchUtilities_waitingForDebugger;

	public static String PHPWebPageLaunchDelegate_serverNotFound;

	public static String DebuggerConfigurationDialog_debugPort;
	public static String DebuggerConfigurationDialog_invalidPort;

	public static String ZendDebuggerConfigurationDialog_runWithDebugInfo;
	public static String ZendDebuggerConfigurationDialog_zendDebugger;
	public static String ZendDebuggerConfigurationDialog_zendDebuggerSettings;

	public static String XDebugConfigurationDialog_invalidTimeout;
	public static String XDebugConfigurationDialog_invalidTimeoutValue;
	public static String XDebugConfigurationDialog_maxArrayDepth;
	public static String XDebugConfigurationDialog_showSuperGlobals;
	public static String XDebugConfigurationDialog_xdebugSettings;
	public static String XDebugConfigurationDialog_invalidPortRange;
	public static String XDebugConfigurationDialog_useMultisession;
	public static String XDebugConfigurationDialog_useProxy;
	public static String XDebugConfigurationDialog_idekey;
	public static String XDebugConfigurationDialog_proxy;
	public static String XDebugConfigurationDialog_proxyGroup;

	
    static {
        // load message values from bundle file
        NLS.initializeMessages(BUNDLE_NAME, PHPDebugCoreMessages.class);
    }

    private PHPDebugCoreMessages() {
        // cannot create new instance
    }
}
