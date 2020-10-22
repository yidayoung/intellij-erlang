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

package org.intellij.erlang.rebar.importWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.utils.ErlangTermFileUtil;
import org.intellij.erlang.rebar.util.RebarConfigUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ImportedOtpApp {
  private String myName;

  private final Set<String> myDeps = new HashSet<>();
  private final Set<VirtualFile> myIncludePaths = new HashSet<>();
  private final Set<VirtualFile> mySourcePaths = new HashSet<>();
  private final Set<VirtualFile> myTestPaths = new HashSet<>();
  private final Set<String> myApps = new HashSet<>();
  private final Set<String> myParseTransforms = new HashSet<>();
  private final Set<String> myGlobalIncludes = new HashSet<>();

  @Nullable
  private String myIdeaModuleFilePath;
  private VirtualFile myRoot;
  private Boolean myIsRebar3;
  private Module myModule;
  private String myAppDirPath;
  private Boolean myIsWriteAble;
  private String myGroup;


  private void InitApp(@NotNull VirtualFile root,
                       @Nullable final VirtualFile appConfig,
                       Boolean isRebar3,
                       Boolean isRoot) {
    myName = appConfig == null ? root.getName() : getApplicationName(appConfig);
    myIsWriteAble = true;
    myIsRebar3 = isRebar3;
    myRoot = root;
    ApplicationManager.getApplication().runReadAction(() -> {
      if (appConfig != null) addDependenciesFromAppFile(appConfig);
      addInfoFromRebarConfig();
      if (isRebar3 && isRoot){
        // rebar3 root module is empty， but has apps
        VirtualFile appsFile = root.findChild("apps");
        if (appsFile != null){
          myAppDirPath = appsFile.getPath();
          RebarConfigUtil.calcApps(appsFile, myApps);
          myDeps.addAll(myApps);
        }
      }
      else {
        addPath(myRoot, "src", mySourcePaths);
        addPath(myRoot, "test", myTestPaths);
        addPath(myRoot, "include", myIncludePaths);
      }
    });
  }
  public ImportedOtpApp(@NotNull VirtualFile root, final VirtualFile appConfig, Boolean isRebar3){
    InitApp(root, appConfig, isRebar3, false);
  }
  public ImportedOtpApp(@NotNull VirtualFile root, Boolean isRebar3) {
    InitApp(root, null, isRebar3, true);
  }

  @NotNull
  private static String getApplicationName(@NotNull VirtualFile appConfig) {
    return StringUtil.trimEnd(StringUtil.trimEnd(appConfig.getName(), ".src"), ".app");
  }

  private static HashSet<VirtualFile> findAppFileFromEbin(VirtualFile ebinRoot) {
    HashSet<VirtualFile> files = new HashSet<>();
    for (VirtualFile file : ebinRoot.getChildren()) {
      if (!file.isDirectory() && file.getName().endsWith("ebin")) files.add(file);
    }
    return files;
  }


  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public VirtualFile getRoot() {
    return myRoot;
  }

  @NotNull
  public Set<String> getDeps() {
    return myDeps;
  }

  public Set<VirtualFile> getIncludePaths() {
    return myIncludePaths;
  }

  public void setIdeaModuleFilePath(@Nullable String ideaModuleFilePath) {
    myIdeaModuleFilePath = ideaModuleFilePath;
  }

  @Nullable
  public String getIdeaModuleFilePath() {
    return myIdeaModuleFilePath;
  }

  public Module getModule() {
    return myModule;
  }

  public void setModule(Module module) {
    myModule = module;
  }

  public Set<String> getParseTransforms() {
    return myParseTransforms;
  }
  public void addParseTransforms(Collection<String> newParseTransforms){
    if (newParseTransforms.isEmpty()) return;
    myParseTransforms.addAll(newParseTransforms);
  }

  @Override
  public String toString() {
    return myName + " (" + myRoot.getPath() + ')';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ImportedOtpApp that = (ImportedOtpApp) o;

    if (!myName.equals(that.myName)) return false;
    if (!myRoot.equals(that.myRoot)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myRoot.hashCode();
    return result;
  }

  private void addInfoFromRebarConfig() {
    VirtualFile rebarConfig = myRoot.findChild("rebar.config");
    ErlangFile rebarConfigPsi = rebarConfig != null ? ErlangTermFileUtil.createPsi(rebarConfig) : null;
    if (rebarConfigPsi == null) return;
    addDependenciesFromRebarConfig(rebarConfigPsi);
    addIncludePathsFromRebarConfig(rebarConfigPsi);
    addParseTransformsFromRebarConfig(rebarConfigPsi);
    addExtraSourceDirFromRebarConfig(rebarConfigPsi);
  }

  private void addExtraSourceDirFromRebarConfig(ErlangFile rebarConfig) {
    RebarConfigUtil.getExtraSrcDirs(rebarConfig)
                   .forEach(path -> addPath(myRoot, path, mySourcePaths));
  }

  private void addDependenciesFromAppFile(@NotNull VirtualFile appFile) {
    ErlangFile appConfigPsi = ErlangTermFileUtil.createPsi(appFile);
    List<ErlangTupleExpression> applicationDescriptors = ErlangTermFileUtil.getConfigSections(appConfigPsi, "application");
    ErlangListExpression appAttributes = PsiTreeUtil.getChildOfType(ContainerUtil.getFirstItem(applicationDescriptors), ErlangListExpression.class);
    ErlangTermFileUtil.processConfigSection(appAttributes, "applications", deps -> {
      ErlangListExpression dependencyAppsList = deps instanceof ErlangListExpression ? (ErlangListExpression) deps : null;
      if (dependencyAppsList != null) {
        for (ErlangExpression depExpression : dependencyAppsList.getExpressionList()) {
          ErlangQAtom depApp = PsiTreeUtil.getChildOfType(depExpression, ErlangQAtom.class);
          ErlangAtom appNameAtom = depApp != null ? depApp.getAtom() : null;
          if (appNameAtom != null) {
            myDeps.add(appNameAtom.getName());
          }
        }
      }
    });
  }

  private void addDependenciesFromRebarConfig(ErlangFile rebarConfig) {
    myDeps.addAll(RebarConfigUtil.getDependencyAppNames(rebarConfig));
  }
  private void addIncludePathsFromRebarConfig(ErlangFile rebarConfig) {
    for (String includePath : RebarConfigUtil.getIncludePaths(rebarConfig)) {
      addPath(myRoot, includePath, myIncludePaths);
    }
  }

  private void addParseTransformsFromRebarConfig(ErlangFile rebarConfig) {
    myParseTransforms.addAll(RebarConfigUtil.getParseTransforms(rebarConfig));
  }

  private static void addPath(VirtualFile base, String relativeIncludePath, Set<VirtualFile> paths) {
    VirtualFile path = VfsUtilCore.findRelativeFile(relativeIncludePath, base);
    if (path != null) {
      paths.add(path);
    }
  }

  public Set<VirtualFile> getSourcePaths() {
    return mySourcePaths;
  }

  public Set<VirtualFile> getTestPaths() {
    return myTestPaths;
  }

  public Set<String> getApps() {
    return myApps;
  }

  public Boolean isRebar3() {
    return myIsRebar3;
  }

  public String getAppDirPath() {
    return myAppDirPath;
  }

  public Boolean getWriteAble() {
    return myIsWriteAble;
  }

  public void setWriteAble(Boolean writeAble) {
    myIsWriteAble = writeAble;
  }

  public String getGroup() {
    return myGroup;
  }

  public void setGroup(String group) {
    myGroup = group;
  }

  public void addGlobalIncludes(Collection<String> newIncludeDirs){
    if (newIncludeDirs.isEmpty()) return;
    myGlobalIncludes.addAll(newIncludeDirs);
  }

  public Set<String> getGlobalIncludes() {
    return myGlobalIncludes;
  }
}
