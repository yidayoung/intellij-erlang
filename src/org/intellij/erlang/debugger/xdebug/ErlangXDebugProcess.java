/*
 * Copyright 2012-2015 Sergey Ignatov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.erlang.debugger.xdebug;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.io.URLUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.intellij.erlang.debugger.node.*;
import org.intellij.erlang.debugger.remote.ErlangRemoteDebugRunConfiguration;
import org.intellij.erlang.debugger.remote.ErlangRemoteDebugRunningState;
import org.intellij.erlang.debugger.xdebug.xvalue.ErlangXValueFactory;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.runconfig.ErlangRunConfigurationBase;
import org.intellij.erlang.runconfig.ErlangRunningState;
import org.intellij.erlang.utils.ErlangModulesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.intellij.erlang.debugger.ErlangDebuggerLog.LOG;

public class ErlangXDebugProcess extends XDebugProcess implements ErlangDebuggerEventListener {
  private static File tempDirectory;
  private final XDebugSession mySession;
  private final ExecutionEnvironment myExecutionEnvironment;
  private final ErlangRunningState myRunningState;
  private final ErlangDebuggerNode myDebuggerNode;
  private final OSProcessHandler myErlangProcessHandler;
  private final ErlangDebugLocationResolver myLocationResolver;

  private final XBreakpointHandler<?>[] myBreakpointHandlers = new XBreakpointHandler[]{new ErlangLineBreakpointHandler(this)};
  private final ConcurrentHashMap<ErlangSourcePosition, XLineBreakpoint<ErlangLineBreakpointProperties>> myPositionToLineBreakpointMap =
    new ConcurrentHashMap<>();
  private Queue<XDebuggerEvaluator.XEvaluationCallback> myCallbackQueue = new LinkedBlockingQueue<>();
  private Set<String> InterpretedModules = new HashSet<>();
  public ErlangXDebugProcess(@NotNull XDebugSession session, ExecutionEnvironment env) throws ExecutionException {
    //TODO add debug build targets and make sure the project is built using them.
    super(session);
    mySession = session;

    session.setPauseActionSupported(false);

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void stackFrameChanged() {
        XDebugSession cSession = getSession();
        XExecutionStack executionStack = ((XDebugSessionImpl) cSession).getCurrentExecutionStack();
        if (executionStack instanceof ErlangExecutionStack){
          ErlangProcessSnapshot Snap = ((ErlangExecutionStack)executionStack).getSnapshot();
          myDebuggerNode.processSuspended(Snap.getPid());
        }
      }
    });

    myExecutionEnvironment = env;
    myRunningState = getRunConfiguration().getState(myExecutionEnvironment.getExecutor(), myExecutionEnvironment);
    if (myRunningState == null) {
      throw new ExecutionException("Failed to execute a run configuration.");
    }

    try {
      //TODO add the debugger node to disposable hierarchy (we may fail to initialize session so the session will not be stopped!)
      myDebuggerNode = new ErlangDebuggerNode(this);
    }
    catch (ErlangDebuggerNodeException e) {
      throw new ExecutionException(e);
    }

    //TODO split running debug target and debugger process spawning
    myErlangProcessHandler = runDebugTarget();
    setModulesToInterpret();
    ErlangRunConfigurationBase<?> runConfig = getRunConfiguration();
    myLocationResolver = new ErlangDebugLocationResolver(runConfig.getProject(),
                                                         runConfig.getConfigurationModule().getModule(),
                                                         runConfig.isUseTestCodePath());
  }

  @NotNull
  public ErlangDebugLocationResolver getLocationResolver() {
    return myLocationResolver;
  }

  public synchronized void evaluateExpression(@NotNull String expression,
                                              @NotNull XDebuggerEvaluator.XEvaluationCallback callback,
                                              @NotNull ErlangTraceElement traceElement,
                                              @Nullable XSourcePosition expressionPosition) {
    myCallbackQueue.add(callback);
    myDebuggerNode.evaluate(expression, traceElement, expressionPosition);
  }

  @Override
  public synchronized void handleEvaluationResponse(OtpErlangObject response) {
    XDebuggerEvaluator.XEvaluationCallback callback = myCallbackQueue.poll();
    if (callback != null) {
      callback.evaluated(ErlangXValueFactory.create(response));
    }
  }

  @Override
  public void debuggerStarted() {
    getSession().reportMessage("Debug process started", MessageType.INFO);
  }

  @Override
  public void failedToInterpretModules(String nodeName, List<String> modules) {
    String messagePrefix = "Failed to interpret modules on node " + nodeName + ": ";
    String modulesString = StringUtil.join(modules, ", ");
    String messageSuffix = ".\nMake sure they are compiled with debug_info option, their sources are located in same directory as .beam files, modules are available on the node.";
    String message = messagePrefix + modulesString + messageSuffix;
    getSession().reportMessage(message, MessageType.WARNING);
  }

  @Override
  public void failedToDebugRemoteNode(String nodeName, String error) {
    String message = "Failed to debug remote node '" + nodeName + "'. Details: " + error;
    getSession().reportMessage(message, MessageType.ERROR);
  }


  @Override
  public void printMessage(String messageText, ConsoleViewContentType type) {
    getSession().getConsoleView().print(messageText + "\n", type);
  }

  
  @Override
  public void unknownMessage(String messageText) {
    getSession().reportMessage("Unknown message received: " + messageText, MessageType.WARNING);
    getSession().getConsoleView().print("Unknown message received: " + messageText + "\n",
                                        ConsoleViewContentType.LOG_WARNING_OUTPUT);
  }

  @Override
  public void failedToSetBreakpoint(String module, int line, String errorMessage) {
    ErlangSourcePosition sourcePosition = ErlangSourcePosition.create(myLocationResolver, module, line);
    XLineBreakpoint<ErlangLineBreakpointProperties> breakpoint = getLineBreakpoint(sourcePosition);
    if (breakpoint != null) {
      getSession().updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, errorMessage);
    }
  }

  @Override
  public void breakpointIsSet(String module, int line) {
  }

  @Override
  public void breakpointReached(final OtpErlangPid pid, List<ErlangProcessSnapshot> snapshots) {
    XSuspendContext suspendContextBase = mySession.getSuspendContext();
    OtpErlangPid lastSuspendedPid = suspendContextBase != null ? ((ErlangSuspendContext)suspendContextBase).getActivePid() :
                                    null;
    if (mySession.isSuspended() && !Objects.equals(lastSuspendedPid, pid)){
      return;
    }
    ErlangProcessSnapshot processInBreakpoint = ContainerUtil.find(snapshots, erlangProcessSnapshot -> erlangProcessSnapshot.getPid().equals(pid));
    if (lastSuspendedPid != null
        && lastSuspendedPid.equals(pid)
        && processInBreakpoint.getBreakLine() == ((ErlangSuspendContext)suspendContextBase).getBreakLine()
        && snapshots.size() == suspendContextBase.getExecutionStacks().length)
      return; //eval cmd will make a old breakpoint msg, but it would make refresh and call eval again, so ignore
    ErlangSourcePosition breakPosition = ErlangSourcePosition.create(myLocationResolver, processInBreakpoint);
    XLineBreakpoint<ErlangLineBreakpointProperties> breakpoint = getLineBreakpoint(breakPosition);
    ErlangSuspendContext suspendContext = new ErlangSuspendContext(this, pid, snapshots);
    if (breakpoint == null) {
      getSession().positionReached(suspendContext);
    }
    else {
      boolean shouldSuspend = getSession().breakpointReached(breakpoint, null, suspendContext);
      if (!shouldSuspend) {
        resume(suspendContext);
      }
    }
  }

  @Override
  public void debuggerStopped() {
    getSession().reportMessage("Debug process stopped", MessageType.INFO);
    getSession().stop();
  }

  @Nullable
  private XLineBreakpoint<ErlangLineBreakpointProperties> getLineBreakpoint(@Nullable ErlangSourcePosition sourcePosition) {
    return sourcePosition != null ? myPositionToLineBreakpointMap.get(sourcePosition) : null;
  }
  private void setModulesToInterpret() {
    ErlangRemoteDebugRunConfiguration runConfig = (ErlangRemoteDebugRunConfiguration) getRunConfiguration();
    Collection<ErlangFile> erlangModules = new ArrayList<>();
    switch (runConfig.getInterpretScope()){
      case ErlangRemoteDebugRunConfiguration.IN_BREAK_POINT_FILE:
        return;
      case ErlangRemoteDebugRunConfiguration.IN_MODULE:
      {
        Module tarModule = runConfig.getConfigurationModule().getModule();
        assert tarModule != null;
        erlangModules = ErlangModulesUtil.getErlangModules(tarModule, runConfig.isUseTestCodePath());
        break;
      }
      case ErlangRemoteDebugRunConfiguration.IN_PROJECT:
      {
        erlangModules = ErlangModulesUtil.getErlangModules(runConfig.getProject());
        break;
      }
    }

    Set<String> notToInterpret = runConfig.getDebugOptions().getModulesNotToInterpret();
    List<String> moduleSourcePaths = new ArrayList<>(erlangModules.size());
    for (ErlangFile erlangModule : erlangModules) {
      VirtualFile file = erlangModule.getVirtualFile();
      if (file != null && !notToInterpret.contains(file.getNameWithoutExtension())) {
        moduleSourcePaths.add(PathUtil.getLocalPath(file));
      }
    }
    InterpretedModules.addAll(moduleSourcePaths);
    myDebuggerNode.interpretModules(moduleSourcePaths);
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    ConsoleView consoleView = myRunningState.createConsoleView(myExecutionEnvironment.getExecutor());
    consoleView.attachToProcess(getProcessHandler());
    myErlangProcessHandler.startNotify();
    return consoleView;
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return new ErlangDebuggerProvider();
  }

  @Override
  public void startStepOver(@Nullable XSuspendContext context) {
    myDebuggerNode.stepOver();
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    myDebuggerNode.stepInto();
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
    myDebuggerNode.stepOut();
  }

  @Override
  public void stop() {
    myDebuggerNode.stop();
  }

  @Override
  public void resume(@Nullable XSuspendContext context) {
    myDebuggerNode.resume();
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
    //TODO implement me
    myDebuggerNode.stepInto();
  }

  @Nullable
  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myErlangProcessHandler;
  }

  void addBreakpoint(XLineBreakpoint<ErlangLineBreakpointProperties> breakpoint) {
    ErlangSourcePosition breakpointPosition = getErlangSourcePosition(breakpoint);
    if (breakpointPosition == null) return;
    myPositionToLineBreakpointMap.put(breakpointPosition, breakpoint);
    String filePath = PathUtil.getLocalPath(breakpointPosition.getFile());
    if (!InterpretedModules.contains(filePath)){
      ArrayList<String> L = new ArrayList<>(1);
      L.add(filePath);
      InterpretedModules.add(filePath);
      myDebuggerNode.interpretModules(L);
    }
    if (breakpoint.getConditionExpression() != null){
      myDebuggerNode.setBreakpoint(breakpointPosition.getErlangModuleName(), breakpointPosition.getLine(), breakpoint.getConditionExpression().getExpression());
    }
    else
      myDebuggerNode.setBreakpoint(breakpointPosition.getErlangModuleName(), breakpointPosition.getLine(), "");
  }

  void removeBreakpoint(XLineBreakpoint<ErlangLineBreakpointProperties> breakpoint,
                        @SuppressWarnings("UnusedParameters") boolean temporary) {
    ErlangSourcePosition breakpointPosition = getErlangSourcePosition(breakpoint);
    if (breakpointPosition == null) return;
    myPositionToLineBreakpointMap.remove(breakpointPosition);
    myDebuggerNode.removeBreakpoint(breakpointPosition.getErlangModuleName(), breakpointPosition.getLine());
  }

  @Nullable
  private static ErlangSourcePosition getErlangSourcePosition(XLineBreakpoint<ErlangLineBreakpointProperties> breakpoint) {
    XSourcePosition sourcePosition = breakpoint.getSourcePosition();
    return sourcePosition != null ? ErlangSourcePosition.create(sourcePosition) : null;
  }

  private ErlangRunConfigurationBase<?> getRunConfiguration() {
    ErlangRunConfigurationBase<?> runConfig = (ErlangRunConfigurationBase) getSession().getRunProfile();
    assert runConfig != null;
    return runConfig;
  }

  @NotNull
  private OSProcessHandler runDebugTarget() throws ExecutionException {
    OSProcessHandler erlangProcessHandler;
    LOG.debug("Preparing to run debug target.");
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      myRunningState.setExePath(commandLine);
//      myRunningState.setWorkDirectory(commandLine);
      setUpErlangDebuggerCodePath(commandLine);
      myRunningState.setCodePath(commandLine);
      commandLine.setWorkDirectory(tempDirectory);
      commandLine.addParameters("-run", "c", "c", "debugnode");
      commandLine.addParameters("-run", "debugnode", "main", String.valueOf(myDebuggerNode.getLocalDebuggerPort()));
      myRunningState.setErlangFlags(commandLine);
      myRunningState.setNoShellMode(commandLine);
      myRunningState.setStopErlang(commandLine);

      LOG.debug("Running debugger process. Command line (platform-independent): ");
      LOG.debug(commandLine.getCommandLineString());

      Process process = commandLine.createProcess();
      erlangProcessHandler = new OSProcessHandler(process, commandLine.getCommandLineString()){
        @NotNull
        @Override
        protected BaseOutputReader.Options readerOptions() {
          return BaseOutputReader.Options.forMostlySilentProcess();
        }
      };

      LOG.debug("Debugger process started.");

      if (myRunningState instanceof ErlangRemoteDebugRunningState) {
        LOG.debug("Initializing remote node debugging.");
        ErlangRemoteDebugRunConfiguration runConfiguration = (ErlangRemoteDebugRunConfiguration) getRunConfiguration();
        if (StringUtil.isEmptyOrSpaces(runConfiguration.getRemoteErlangNodeName())) {
          throw new ExecutionException("Bad run configuration: remote Erlang node is not specified.");
        }
        LOG.debug("Remote node: " + runConfiguration.getRemoteErlangNodeName());
        LOG.debug("Cookie: " + runConfiguration.getCookie());
        myDebuggerNode.debugRemoteNode(runConfiguration.getRemoteErlangNodeName(), runConfiguration.getCookie());
      }
      else {
        LOG.debug("Initializing local debugging.");
        ErlangRunningState.ErlangEntryPoint entryPoint = myRunningState.getDebugEntryPoint();
        LOG.debug("Entry point: " + entryPoint.getModuleName() + ":" + entryPoint.getFunctionName() +
                  "(" + StringUtil.join(entryPoint.getArgsList(), ", ") + ")");
        myDebuggerNode.runDebugger(entryPoint.getModuleName(), entryPoint.getFunctionName(), entryPoint.getArgsList());
      }
    }
    catch (ExecutionException e) {
      LOG.debug("Failed to run debug target.", e);
      throw e;
    }
    LOG.debug("Debug target should now be running.");
    return erlangProcessHandler;
  }

  private static void setUpErlangDebuggerCodePath(GeneralCommandLine commandLine) throws ExecutionException {
    LOG.debug("Setting up debugger environment.");
    try {
      String[] files = {"debug_condition.erl", "debug_eval.erl", "debugnode.erl",
                        "remote_debugger.erl", "remote_debugger_listener.erl",
                        "remote_debugger_notifier.erl", "process_names.hrl",
                        "remote_debugger_messages.hrl", "trace_utils.hrl"};
      tempDirectory = FileUtil.createTempDirectory("intellij_erlang_debugger_", null, true);
      LOG.debug("Debugger beams will be put to: " + tempDirectory.getPath());
      copyFiles(files, tempDirectory, "/debugger/src");
      LOG.debug("Debugger beams were copied successfully.");
      commandLine.addParameters("-pa", tempDirectory.getPath());
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to setup debugger environment", e);
    }
  }

  private static void copyFiles(String[] files, File directory, String basePath) throws IOException {
    for (String filename:files) {
      URL baseUrl = ResourceUtil.getResource(ErlangXDebugProcess.class, basePath,  filename);
      if (baseUrl == null) {
        throw new IOException("Failed to locate debugger module: " + filename);
      }
      try (BufferedInputStream inputStream = new BufferedInputStream(URLUtil.openStream(baseUrl))) {
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File(directory, filename)))) {
          FileUtil.copy(inputStream, outputStream);
        }
      }
    }
  }
}
