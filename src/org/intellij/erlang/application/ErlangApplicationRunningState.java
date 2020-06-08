/*
 * Copyright 2012-2014 Sergey Ignatov
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

package org.intellij.erlang.application;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.erlang.console.ErlangConsoleUtil;
import org.intellij.erlang.console.ErlangConsoleView;
import org.intellij.erlang.runconfig.ErlangRunningState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangApplicationRunningState extends ErlangRunningState {
  private final ErlangApplicationConfiguration myConfiguration;

  public ErlangApplicationRunningState(ExecutionEnvironment env, Module module, ErlangApplicationConfiguration configuration) {
    super(env, module);
    myConfiguration = configuration;
  }

  @Override
  protected TextConsoleBuilder getConsoleBuilder(Project project) {
    return new TextConsoleBuilderImpl(project) {
      @NotNull
      @Override
      public ConsoleView getConsole() {
        ErlangConsoleView consoleView = new ErlangConsoleView(project);
        ErlangConsoleUtil.attachFilters(project, consoleView);
        return consoleView;
      }
    };
  }

  @Override
  protected boolean useTestCodePath() {
    return myConfiguration.isUseTestCodePath();
  }

  @Override
  protected boolean isNoShellMode() {
    return false;
  }

  @Override
  protected boolean isStopErlang() {
    return myConfiguration.isStopErlang();
  }

  @Override
  protected List<String> getErlFlags() {
    return StringUtil.split(myConfiguration.getErlFlags(), " ");
  }

  @Nullable
  @Override
  public ErlangEntryPoint getEntryPoint() throws ExecutionException {
    ErlangEntryPoint entryPoint = ErlangEntryPoint.fromModuleAndFunction(myConfiguration.getModuleAndFunction(), myConfiguration.getParams());
    if (entryPoint == null) {
      throw new ExecutionException("Invalid entry point");
    }
    return entryPoint;
  }

  @Nullable
  @Override
  public String getWorkDirectory() {
    return myConfiguration.getWorkDirectory();
  }

  @NotNull
  @Override
  public ConsoleView createConsoleView(Executor executor) {
    ErlangConsoleView consoleView = new ErlangConsoleView(myConfiguration.getProject());
    ErlangConsoleUtil.attachFilters(myConfiguration.getProject(), consoleView);
    return consoleView;
  }
}
