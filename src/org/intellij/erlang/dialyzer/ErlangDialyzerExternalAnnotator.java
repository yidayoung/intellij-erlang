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

package org.intellij.erlang.dialyzer;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.roots.ErlangIncludeDirectoryUtil;
import org.intellij.erlang.sdk.ErlangSystemUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErlangDialyzerExternalAnnotator extends ExternalAnnotator<ErlangDialyzerExternalAnnotator.State, ErlangDialyzerExternalAnnotator.State> {
  private final static Logger LOG = Logger.getInstance(ErlangDialyzerExternalAnnotator.class);
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Dialyzer-based inspections");
  
  @Nullable
  private static Problem parseProblem(String input) {

    List<String> split = StringUtil.split(input, ":");
    if (split.size() < 3) return null;
    int line = StringUtil.parseInt(split.get(1), 0);
    return new Problem(line, StringUtil.join(split.subList(2, split.size()), ":"));
  }

  @Nullable
  @Override
  public State collectInformation(@NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null || vFile.getFileType() != ErlangFileType.MODULE) return null;
    String canonicalPath = vFile.getCanonicalPath();
    if (canonicalPath == null) return null;
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return null;
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null) return null;
    String homePath = sdk.getHomePath();
    if (homePath == null) return null;

    InspectionProfile profile = InspectionProjectProfileManager.getInstance(file.getProject()).getCurrentProfile();
    HighlightDisplayKey key = HighlightDisplayKey.find(ErlangDialyzerInspection.INSPECTION_SHORT_NAME);
    if (!profile.isToolEnabled(key)) return null;

    String workingDir = file.getProject().getBasePath();
    String dialyzerPath = homePath + "/bin/dialyzer" + (SystemInfo.isWindows ? ".exe" : "");

    String currentPltPath = DialyzerSettings.getInstance(file.getProject()).getCurrentPltPath();
    List<VirtualFile> includeDirectories = ErlangIncludeDirectoryUtil.getIncludeDirectories(module);
    return new State(dialyzerPath, currentPltPath, canonicalPath, workingDir, includeDirectories);
  }

  @Nullable
  @Override
  public State doAnnotate(State state) {
    if (state == null) return null;

    ProcessOutput output = null;
    try {
      String[] params = StringUtil.isEmptyOrSpaces(state.myCurrentPltPath) ? new String[]{state.myFilePath} :
                        new String[]{"--plt", state.myCurrentPltPath, state.myFilePath};
      if(state.myIncludeDirectories.size() > 0){
        ArrayList<String> includes = new ArrayList<>();
        includes.add("-I");
        for (VirtualFile dir : state.myIncludeDirectories){
          includes.add(dir.getPath());
        }
        ContainerUtil.addAllNotNull(includes, params);
        params = new String[includes.size()];
        includes.toArray(params);
      }
      output = ErlangSystemUtil.getProcessOutput(state.myWorkingDir, state.myDialyzerPath, params);
    } catch (ExecutionException e) {
      LOG.debug(e);
    }
    if (output != null) {
      if (output.getStderrLines().isEmpty()) {
        String stdout = output.getStdout();
        if (stdout.indexOf("dialyzer: ") > 0){
          NOTIFICATION_GROUP.createNotification(stdout, NotificationType.WARNING).notify(null); // todo: get a project
          return state;
        }
        String BEGIN_STR="Proceeding with analysis...";
        String END_STR = "done in";
        int begin = stdout.indexOf(BEGIN_STR) + BEGIN_STR.length();
        int end = stdout.indexOf(END_STR);
        if (begin>BEGIN_STR.length() && end > begin)
        {
          stdout = stdout.substring(begin, end);
          Pattern pattern = Pattern.compile("^[0-9 a-z_A-Z\\-\\\\./]+:(\\d+):", Pattern.MULTILINE);
          Matcher matcher = pattern.matcher(stdout);
          boolean find =matcher.find();
          while (find){
            int line = StringUtil.parseInt(matcher.group(1), 0);
            int this_begin = matcher.end();
            find = matcher.find();
            int this_end = find?matcher.start():stdout.length();
            Problem problem = new Problem(line, stdout.substring(this_begin, this_end));
            LOG.debug(problem.toString());
            ContainerUtil.addAllNotNull(state.problems, problem);
          }
        }

      }
    }
    return state;
  }

  @Override
  public void apply(@NotNull PsiFile file, State annotationResult, @NotNull AnnotationHolder holder) {
    if (annotationResult == null || !file.isValid()) return;
    String text = file.getText();
    for (Problem problem : annotationResult.problems) {
      int offset = StringUtil.lineColToOffset(text, problem.myLine - 1, 0);

      if (offset == -1) continue;

      int width = 0;
      while (offset + width < text.length() && !StringUtil.isLineBreak(text.charAt(offset + width))) width++;

      TextRange problemRange = TextRange.create(offset, offset + width);
      String message = "Dialyzer: " + problem.myDescription;
      Annotation annotation = holder.createWarningAnnotation(problemRange, message);
      HighlightDisplayKey key = HighlightDisplayKey.find(ErlangDialyzerInspection.INSPECTION_SHORT_NAME);
      annotation.registerFix(new DisableInspectionToolAction(key) {
        @NotNull
        @Override
        public String getName() {
          return "Disable 'Dialyzer-based inspections'";
        }
      });
    }
  }

  public static class Problem {
    private final int myLine;
    private final String myDescription;

    public Problem(int line, String description) {
      myLine = line;
      myDescription = description;
    }

    @Override
    public String toString() {
      return "Problem{" +
        "myLine=" + myLine +
        ", myDescription='" + myDescription + '\'' +
        '}';
    }
  }

  public static class State {
    public final List<Problem> problems = new ArrayList<>();
    private final String myDialyzerPath;
    private final String myCurrentPltPath;
    private final String myFilePath;
    private final String myWorkingDir;
    private final List<VirtualFile> myIncludeDirectories;

    public State(String dialyzerPath,
                 String currentPltPath,
                 String filePath,
                 String workingDir,
                 List<VirtualFile> includeDirectories) {
      myDialyzerPath = dialyzerPath;
      myCurrentPltPath = currentPltPath;
      myFilePath = filePath;
      myWorkingDir = workingDir;
      myIncludeDirectories = includeDirectories;
    }
  }
}
