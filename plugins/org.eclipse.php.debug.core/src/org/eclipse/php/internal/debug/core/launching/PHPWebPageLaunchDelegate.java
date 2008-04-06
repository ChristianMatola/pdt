package org.eclipse.php.internal.debug.core.launching;

import java.text.MessageFormat;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.php.debug.core.debugger.parameters.IDebugParametersKeys;
import org.eclipse.php.internal.debug.core.IPHPDebugConstants;
import org.eclipse.php.internal.debug.core.Logger;
import org.eclipse.php.internal.debug.core.PHPDebugCoreMessages;
import org.eclipse.php.internal.debug.core.PHPDebugPlugin;
import org.eclipse.php.internal.debug.core.preferences.PHPProjectPreferences;
import org.eclipse.php.internal.debug.core.zend.communication.DebuggerCommunicationDaemon;
import org.eclipse.php.internal.debug.core.zend.debugger.IDebuggerInitializer;
import org.eclipse.php.internal.debug.core.zend.debugger.PHPSessionLaunchMapper;
import org.eclipse.php.internal.debug.core.zend.debugger.PHPWebServerDebuggerInitializer;
import org.eclipse.php.internal.debug.daemon.DaemonPlugin;
import org.eclipse.php.internal.server.core.Server;
import org.eclipse.php.internal.server.core.manager.ServersManager;
import org.eclipse.swt.widgets.Display;

/**
 * A launch configuration delegate class for launching a PHP web page script.
 *
 * @author shalom
 *
 */
public class PHPWebPageLaunchDelegate extends LaunchConfigurationDelegate {

	protected Job runDispatch;
	protected ILaunch launch;
	protected IDebuggerInitializer debuggerInitializer;

	public PHPWebPageLaunchDelegate() {
		debuggerInitializer = createDebuggerInitilizer();
	}

	/**
	 * Override the extended getLaunch to create a PHPLaunch.
	 */
	public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
		return new PHPLaunch(configuration, mode, null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.php.internal.server.core.launch.IHTTPServerLaunch#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		// Check that the debug daemon is functional
		// DEBUGGER - Make sure that the active debugger id is indeed Zend's debugger
		if (!DaemonPlugin.getDefault().validateCommunicationDaemons(DebuggerCommunicationDaemon.ZEND_DEBUGGER_ID)) {
			monitor.setCanceled(true);
			monitor.done();
			return;
		}
		// Check for previous launches
		if (!PHPLaunchUtilities.notifyPreviousLaunches(launch)) {
			monitor.setCanceled(true);
			monitor.done();
			return;
		}
		if (!PHPLaunchUtilities.checkDebugAllPages(configuration, launch)) {
			monitor.setCanceled(true);
			monitor.done();
			return;
		}
		PHPLaunchUtilities.showDebugView();
		this.launch = launch;
		Server server = ServersManager.getServer(configuration.getAttribute(Server.NAME, ""));
		if (server == null) {
			Logger.log(Logger.ERROR, "Launch configuration could not find server");
			terminated();
			// throw CoreException();
			return;
		}
		String fileName = configuration.getAttribute(Server.FILE_NAME, (String) null);
		// Get the project from the file name
		IPath filePath = new Path(fileName);
		IProject proj = null;
		try {
			proj = ResourcesPlugin.getWorkspace().getRoot().getProject(filePath.segment(0));
		} catch (Throwable t) {
		}

		ILaunchConfigurationWorkingCopy wc = configuration.getWorkingCopy();
		String project = proj.getFullPath().toString();
		wc.setAttribute(IPHPDebugConstants.PHP_Project, project);

		// Set transfer encoding:
		wc.setAttribute(IDebugParametersKeys.TRANSFER_ENCODING, PHPProjectPreferences.getTransferEncoding(proj));
		wc.setAttribute(IDebugParametersKeys.OUTPUT_ENCODING, PHPProjectPreferences.getOutputEncoding(proj));
		wc.setAttribute(IDebugParametersKeys.PHP_DEBUG_TYPE, IDebugParametersKeys.PHP_WEB_PAGE_DEBUG);
		wc.doSave();

		String URL = new String(configuration.getAttribute(Server.BASE_URL, "").getBytes());
		boolean isDebugLaunch = mode.equals(ILaunchManager.DEBUG_MODE);
		if (isDebugLaunch) {
			boolean stopAtFirstLine = wc.getAttribute(IDebugParametersKeys.FIRST_LINE_BREAKPOINT, PHPProjectPreferences.getStopAtFirstLine(proj));
			launch.setAttribute(IDebugParametersKeys.FIRST_LINE_BREAKPOINT, Boolean.toString(stopAtFirstLine));
		}
		int requestPort = PHPDebugPlugin.getDebugPort(DebuggerCommunicationDaemon.ZEND_DEBUGGER_ID);

		// Generate a session id for this launch and put it in the map
		int sessionID = DebugSessionIdGenerator.generateSessionID();
		PHPSessionLaunchMapper.put(sessionID, launch);

		// Fill all rest of the attributes:
		launch.setAttribute(IDebugParametersKeys.PORT, Integer.toString(requestPort));
		launch.setAttribute(IDebugParametersKeys.WEB_SERVER_DEBUGGER, Boolean.toString(true));
		launch.setAttribute(IDebugParametersKeys.ORIGINAL_URL, URL);
		launch.setAttribute(IDebugParametersKeys.SESSION_ID, Integer.toString(sessionID));

		// Trigger the session by initiating a debug request to the debug server
		runDispatch = new RunDispatchJobWebServer(launch);
		runDispatch.schedule();
	}

	/*
	 * Override the super preLaunchCheck to make sure that the server we are using is still valid.
	 * If not, notify the user that a change should be made and open the launch configuration page to do so.
	 *
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#preLaunchCheck(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean preLaunchCheck(final ILaunchConfiguration configuration, final String mode, IProgressMonitor monitor) throws CoreException {
		// Check if the server exists
		final String serverName = configuration.getAttribute(Server.NAME, "");
		Server server = ServersManager.getServer(serverName);
		if (server == null) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					MessageDialog.openWarning(Display.getDefault().getActiveShell(), PHPDebugCoreMessages.PHPLaunchUtilities_phpLaunchTitle, MessageFormat.format(PHPDebugCoreMessages.PHPWebPageLaunchDelegate_serverNotFound, new String[] { serverName }));
					PHPLaunchUtilities.openLaunchConfigurationDialog(configuration, mode);
				}
			});
			return false;
		}

		return super.preLaunchCheck(configuration, mode, monitor);
	}

	/**
	 * Initiate a debug session.
	 *
	 * @param launch
	 */
	protected void initiateDebug(ILaunch launch) {
		try {
			debuggerInitializer.debug(launch);
		} catch (DebugException e) {
			IStatus status = e.getStatus();
			String errorMessage = null;
			if (status == null) {
				Logger.traceException("Unexpected Error return from debuggerInitializer ", e);
				fireError(PHPDebugCoreMessages.Debugger_Unexpected_Error_1, e);
				errorMessage = PHPDebugCoreMessages.Debugger_Unexpected_Error_1;
			} else {
				fireError(status);
				errorMessage = status.getMessage();
			}
			displayErrorMessage(errorMessage);
		}
	}

	/**
	 * Create an {@link IDebuggerInitializer}.
	 *
	 * @return An {@link IDebuggerInitializer} instance.
	 */
	protected IDebuggerInitializer createDebuggerInitilizer() {
		return new PHPWebServerDebuggerInitializer();
	}

	/**
	 * Displays a dialod with an error message.
	 *
	 * @param message The error to display.
	 */
	protected void displayErrorMessage(final String message) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				MessageDialog.openError(Display.getDefault().getActiveShell(), "Debug Error", message);
			}
		});
	}

	/**
	 * Throws a IStatus in a Debug Event
	 *
	 */
	public void fireError(IStatus status) {
		DebugEvent event = new DebugEvent(this, DebugEvent.MODEL_SPECIFIC);
		event.setData(status);
		fireEvent(event);
	}

	/**
	 * Throws a IStatus in a Debug Event
	 *
	 */
	public void fireError(String errorMessage, Exception e) {
		Status status = new Status(IStatus.ERROR, PHPDebugPlugin.getID(), IPHPDebugConstants.INTERNAL_ERROR, errorMessage, e);
		DebugEvent event = new DebugEvent(this, DebugEvent.MODEL_SPECIFIC);
		event.setData(status);
		fireEvent(event);
	}

	/**
	 * Called when the debug session was terminated.
	 */
	public void terminated() {
		DebugEvent event = null;
		if (launch.getDebugTarget() == null) {
			// We have to force the termination of the ILaunch because at this stage there is no
			// PHPDebugTarget, thus we create a dummy debug target to overcome this issue and terminate the launch.
			IDebugTarget dummyDebugTarget = new DummyDebugTarget(launch);
			event = new DebugEvent(dummyDebugTarget, DebugEvent.TERMINATE);
			if (launch != null) {
				launch.addDebugTarget(dummyDebugTarget);
				IDebugEventSetListener launchListener = (IDebugEventSetListener) launch;
				launchListener.handleDebugEvents(new DebugEvent[] { event });
			}
		}
		event = new DebugEvent(this, DebugEvent.TERMINATE);
		fireEvent(event);
	}

	/**
	 * Fires a debug event
	 *
	 * @param event 	The event to be fired
	 */
	public void fireEvent(DebugEvent event) {
		DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[] { event });
	}

	/*
	 * Run is seperate thread so launch doesn't hang.
	 */
	protected class RunDispatchJobWebServer extends Job {
		private ILaunch launch;

		public RunDispatchJobWebServer(ILaunch launch) {
			super("runPHPWebServer");
			this.launch = launch;
			setSystem(true);
		}

		protected IStatus run(IProgressMonitor monitor) {
			initiateDebug(launch);
			Logger.debugMSG("[" + this + "] PHPDebugTarget: Calling Terminated()");
			terminated();
			return Status.OK_STATUS;
		}

		public String toString() {
			String className = getClass().getName();
			className = className.substring(className.lastIndexOf('.') + 1);
			return className + "@" + Integer.toHexString(hashCode());
		}
	}

	private static IThread[] EMPTY_THREADS = new IThread[0];

	/*
	 * A dummy debug target for the termination of the ILaunch.
	 */
	private class DummyDebugTarget implements IDebugTarget {

		private ILaunch launch;

		public DummyDebugTarget(ILaunch launch) {
			this.launch = launch;
		}

		public String getName() throws DebugException {
			return "Session Terminated";
		}

		public IProcess getProcess() {
			return null;
		}

		public IThread[] getThreads() throws DebugException {
			return EMPTY_THREADS;
		}

		public boolean hasThreads() throws DebugException {
			return false;
		}

		public boolean supportsBreakpoint(IBreakpoint breakpoint) {
			return false;
		}

		public IDebugTarget getDebugTarget() {
			return this;
		}

		public ILaunch getLaunch() {
			return launch;
		}

		public String getModelIdentifier() {
			return "";
		}

		public Object getAdapter(Class adapter) {
			return null;
		}

		public boolean canTerminate() {
			return false;
		}

		public boolean isTerminated() {
			return true;
		}

		public void terminate() throws DebugException {
		}

		public boolean canResume() {
			return false;
		}

		public boolean canSuspend() {
			return false;
		}

		public boolean isSuspended() {
			return false;
		}

		public void resume() throws DebugException {
		}

		public void suspend() throws DebugException {
		}

		public void breakpointAdded(IBreakpoint breakpoint) {
		}

		public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {
		}

		public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {
		}

		public boolean canDisconnect() {
			return false;
		}

		public void disconnect() throws DebugException {
		}

		public boolean isDisconnected() {
			return false;
		}

		public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
			return null;
		}

		public boolean supportsStorageRetrieval() {
			return false;
		}
	}
}
