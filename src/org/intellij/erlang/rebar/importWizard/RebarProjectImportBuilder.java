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

package org.intellij.erlang.rebar.importWizard;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.configuration.ErlangCompilerSettings;
import org.intellij.erlang.facet.ErlangFacet;
import org.intellij.erlang.facet.ErlangFacetConfiguration;
import org.intellij.erlang.icons.ErlangIcons;
import org.intellij.erlang.module.ErlangModuleType;
import org.intellij.erlang.rebar.settings.RebarSettings;
import org.intellij.erlang.roots.ErlangIncludeDirectoryUtil;
import org.intellij.erlang.sdk.ErlangSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class RebarProjectImportBuilder extends ProjectImportBuilder<ImportedOtpApp> {
  private static final Logger LOG = Logger.getInstance(RebarProjectImportBuilder.class);

  private boolean myOpenProjectSettingsAfter = false;
  @Nullable
  private VirtualFile myProjectRoot = null;
  private ImportedOtpApp rootApp = null;
  @NotNull
  private List<ImportedOtpApp> myFoundOtpApps = Collections.emptyList();
  @NotNull
  private List<ImportedOtpApp> mySelectedOtpApps = Collections.emptyList();
  private boolean myImportExamples;
  @NotNull
  private String myRebarPath = "";
  private boolean myIsImportingProject;

  private static final String REBAR3_BUILD_PATH = FileUtil.join("_build", "default");
  private static final String REBAR3_TEST_BUILD_PATH = FileUtil.join("_build", "test");

  private RebarProjectRootStep rootStep;

  @NotNull
  @NonNls
  @Override
  public String getName() {
    return "Rebar";
  }

  @Override
  public Icon getIcon() {
    return ErlangIcons.REBAR;
  }

  @Override
  public boolean isSuitableSdkType(@NotNull SdkTypeId sdkType) {
    return sdkType == ErlangSdkType.getInstance();
  }

  @Override
  public List<ImportedOtpApp> getList() {
    return new ArrayList<>(myFoundOtpApps);
  }

  @Override
  public void setList(@Nullable List<ImportedOtpApp> selectedOtpApps) {
    if (selectedOtpApps != null) {
      mySelectedOtpApps = selectedOtpApps;
    }
  }

  @Override
  public boolean isMarked(@Nullable ImportedOtpApp importedOtpApp) {
    return importedOtpApp != null && mySelectedOtpApps.contains(importedOtpApp);
  }

  @Override
  public boolean isOpenProjectSettingsAfter() {
    return myOpenProjectSettingsAfter;
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean openProjectSettingsAfter) {
    myOpenProjectSettingsAfter = openProjectSettingsAfter;
  }

  @Override
  public void cleanup() {
    myOpenProjectSettingsAfter = false;
    myProjectRoot = null;
    myFoundOtpApps = Collections.emptyList();
    mySelectedOtpApps = Collections.emptyList();
  }

  public boolean setProjectRoot(@NotNull final VirtualFile projectRoot) {
    if (projectRoot.equals(myProjectRoot)) {
      return true;
    }
    myProjectRoot = projectRoot;

    boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (!unitTestMode && projectRoot instanceof VirtualDirectoryImpl) {
      ((VirtualDirectoryImpl) projectRoot).refreshAndFindChild("deps");
      ((VirtualDirectoryImpl) projectRoot).refreshAndFindChild("_build");
    }

    VirtualFile rootRebar = myProjectRoot.findChild("rebar.config");
    if (rootRebar == null) {
      Messages.showErrorDialog("no rebar.config in root", "Rebar Project Import");
      return false;
    }

    boolean isRebar3 = (null != myProjectRoot.findChild("_build"));
    ProgressManager.getInstance().run(new Task.Modal(getCurrentProject(), "Scanning Rebar Projects", true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        rootApp = new ImportedOtpApp(projectRoot, isRebar3);
        myFoundOtpApps = createAppsOtpApps(indicator, projectRoot);
        myFoundOtpApps.addAll(createDepsOtpApps(indicator, projectRoot));
      }
    });

    myFoundOtpApps.sort((o1, o2) -> {
      int compareByParentPath = String.CASE_INSENSITIVE_ORDER.compare(o1.getRoot().getParent().getPath(), o2.getRoot().getParent().getPath());
      if (compareByParentPath == 0) {
        return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
      }
      return compareByParentPath;
    });
    ContainerUtil.addIfNotNull(myFoundOtpApps, rootApp);
    mySelectedOtpApps = new ArrayList<>(myFoundOtpApps);
    return !myFoundOtpApps.isEmpty();
  }


  @NotNull
  private ArrayList<ImportedOtpApp> createDepsOtpApps(@NotNull ProgressIndicator indicator,
                                                      @NotNull VirtualFile projectRoot) {
    VirtualFile depsRoot = getDepsDir(projectRoot);
    final ArrayList<ImportedOtpApp> importedOtpApps = new ArrayList<>();
    if (depsRoot == null) {
      return importedOtpApps;
    }
    VfsUtilCore.visitChildrenRecursively(depsRoot, new VirtualFileVisitor<Void>(VirtualFileVisitor.SKIP_ROOT) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        indicator.checkCanceled();
        if (file.isDirectory()) {
          indicator.setText2(file.getPath());
          if (isExamplesDirectory(file) || isRelDirectory(projectRoot.getPath(), file.getPath())) return false;
          if (rootApp.isRebar3() && rootApp.getApps().contains(file.getName())) {
            return false;
          }
        }
        ImportedOtpApp importedOtpApp = createImportedOtpApp(file, rootApp.isRebar3());
        if (null != importedOtpApp){
          importedOtpApp.setGroup("lib");
          ContainerUtil.addAllNotNull(importedOtpApps, importedOtpApp);
        }
        return false;
      }
    });
    return importedOtpApps;
  }

  private ArrayList<ImportedOtpApp> createAppsOtpApps(@NotNull ProgressIndicator indicator,
                                                      @NotNull VirtualFile projectRoot){
    VirtualFile appsRoot = projectRoot.findChild("apps");
    final ArrayList<ImportedOtpApp> importedOtpApps = new ArrayList<>();
    if (appsRoot == null) {
      return importedOtpApps;
    }
    Boolean isRebar3 = rootApp.isRebar3();
    List<String> globalIncludes = ContainerUtil.map(rootApp.getIncludePaths(), VirtualFile::getPath);
    VfsUtilCore.visitChildrenRecursively(appsRoot, new VirtualFileVisitor<Void>(VirtualFileVisitor.SKIP_ROOT) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        indicator.checkCanceled();
        if (file.isDirectory()) {
          indicator.setText2(file.getPath());
          if (isRebar3 && rootApp.getApps().contains(file.getName())) {
            ImportedOtpApp importedOtpApp = createImportedOtpApp(file, isRebar3);
            if (null != importedOtpApp){
              importedOtpApp.setGroup("apps");
              importedOtpApp.addGlobalIncludes(globalIncludes);
              importedOtpApp.addParseTransforms(rootApp.getParseTransforms());
              ContainerUtil.addAllNotNull(importedOtpApps, importedOtpApp);
            }
          }
        }
        return false;
      }
    });
    return importedOtpApps;
  }


  @Nullable
  private static VirtualFile getDepsDir(@NotNull VirtualFile projectRoot) {
    VirtualFile deps = projectRoot.findFileByRelativePath("_build/default/lib");
    return deps != null ? deps : projectRoot.findChild("deps");
  }

  private static boolean isRelDirectory(String projectRootPath, String path) {
    return (projectRootPath + "/rel").equals(path);
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @Override
  public boolean validate(Project current, Project dest) {
    if (!findIdeaModuleFiles(mySelectedOtpApps)) {
      return true;
    }
    int resultCode = Messages.showYesNoCancelDialog(
      ApplicationInfoEx.getInstanceEx().getFullApplicationName() + " old format module files found:\n\n" +
      StringUtil.join(mySelectedOtpApps, importedOtpApp -> {
        String ideaModuleFilePath = importedOtpApp.getIdeaModuleFilePath();
        return ideaModuleFilePath != null ? "    " + ideaModuleFilePath + "\n" : "";
      }, "") +
      "\nWould you like to reuse them?", "Old format module files found",
      Messages.getQuestionIcon());
    if (resultCode == Messages.YES) {
      for (ImportedOtpApp app : mySelectedOtpApps) {
        app.setWriteAble(false);
      }
      return true;
    }
    if (resultCode == Messages.NO){
      try {
        deleteIdeaModuleFiles(mySelectedOtpApps);
        return true;
      }
      catch (IOException e) {
        LOG.error(e);
        return false;
      }
    }
    else {
      return false;
    }
  }

  private static void deleteIdeaModuleFiles(@NotNull final List<ImportedOtpApp> importedOtpApps) throws IOException {
    final IOException[] ex = new IOException[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (ImportedOtpApp importedOtpApp : importedOtpApps) {
          VirtualFile ideaModuleFile = null;
          if (importedOtpApp.getIdeaModuleFilePath() != null) {
            ideaModuleFile = LocalFileSystem.getInstance().findFileByPath(importedOtpApp.getIdeaModuleFilePath());
          }
          if (ideaModuleFile != null) {
            try {
              ideaModuleFile.delete(this);
              importedOtpApp.setIdeaModuleFilePath(null);
            }
            catch (IOException e) {
              ex[0] = e;
            }
          }
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
  }

  @Override
  public List<Module> commit(@NotNull Project project,
                             @Nullable ModifiableModuleModel moduleModel,
                             @NotNull ModulesProvider modulesProvider,
                             @Nullable ModifiableArtifactModel modifiableArtifactModel) {
    ModifiableModuleModel obtainedModuleModel =
      moduleModel != null ? moduleModel : ModuleManager.getInstance(project).getModifiableModel();

    Set<String> projectAppNames = new HashSet<>();
    for (ImportedOtpApp importedOtpApp : mySelectedOtpApps) {
      projectAppNames.add(importedOtpApp.getName());
    }
    Module[] modules = obtainedModuleModel.getModules();
    for (Module value : modules) {
      projectAppNames.add(value.getName());
    }

    Sdk projectSdk = fixProjectSdk(project);
    List<Module> createdModules = new ArrayList<>();

    for (ImportedOtpApp importedOtpApp : mySelectedOtpApps) {
      if (importedOtpApp.getWriteAble()) {
        Module obtainedModule = obtainedModuleModel.findModuleByName(importedOtpApp.getName());
        String ideaModuleFile = project.getBasePath() + "/.modules/" +
                                importedOtpApp.getName() + ModuleFileType.DOT_DEFAULT_EXTENSION;
        Module module;
        if (obtainedModule != null){
          module = obtainedModule;
        }
        else {
          module = obtainedModuleModel.newModule(ideaModuleFile, ErlangModuleType.getInstance().getId());
          createdModules.add(module);
        }
        importedOtpApp.setModule(module);
        ModuleRootModificationUtil.updateModel(module, rootModel -> {
          rootModel.clear();
          addModuleContent(importedOtpApp, rootModel);
          setCompilerOutputPath(project, importedOtpApp, rootModel);

          if (importedOtpApp.getGroup()!=null) {
            String[] groupPath = {importedOtpApp.getGroup()};
            obtainedModuleModel.setModuleGroupPath(module, groupPath);
          }
          else obtainedModuleModel.setModuleGroupPath(module, null);
          // Set inter-module dependencies
          Set<String> unResolveModules = resolveModuleDeps(rootModel, importedOtpApp, projectSdk, projectAppNames);
          if (unResolveModules.size() > 0) {
            Messages.showWarningDialog(String.format("Module %s has modules not find:", importedOtpApp.getName()) + unResolveModules, "Rebar Import");
          }
        });

      }
    }
    // Commit project structure.
    LOG.info("Commit project structure");
    addErlangFacets(mySelectedOtpApps);
    RebarSettings.getInstance(project).setRebarPath(myRebarPath);
    if (myIsImportingProject) {
      ErlangCompilerSettings.getInstance(project).setUseRebarCompilerEnabled(true);
      ApplicationManager.getApplication().runWriteAction(obtainedModuleModel::commit);
    }
    CompilerWorkspaceConfiguration.getInstance(project).CLEAR_OUTPUT_DIRECTORY = false;
    ProjectStructureConfigurable.getInstance(project).getModulesConfig().reset();
    return createdModules;
  }

  private static void setCompilerOutputPath(Project project,
                                            ImportedOtpApp importedOtpApp,
                                            ModifiableRootModel rootModel) {
    String moduleLibDirUrl = VfsUtilCore.pathToUrl(FileUtil.join(project.getBasePath(), REBAR3_BUILD_PATH));
    String moduleTestLibDirUrl = VfsUtilCore.pathToUrl(FileUtil.join(project.getBasePath(), REBAR3_TEST_BUILD_PATH));
    VirtualFile ideaModuleDir = importedOtpApp.getRoot();
    // Initialize output paths according to Rebar conventions.
    CompilerModuleExtension compilerModuleExt = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExt.inheritCompilerOutputPath(false);

    if (importedOtpApp.isRebar3()) {
      if (importedOtpApp.getAppDirPath() != null) {
        compilerModuleExt.setCompilerOutputPath(moduleLibDirUrl);
        compilerModuleExt.setCompilerOutputPathForTests(moduleTestLibDirUrl);
      }
      else {
        compilerModuleExt.setCompilerOutputPath(moduleLibDirUrl + File.separator + FileUtil.join("lib", importedOtpApp.getName(), "ebin"));
        compilerModuleExt.setCompilerOutputPathForTests(moduleTestLibDirUrl + File.separator + FileUtil.join("lib", importedOtpApp.getName(), "ebin"));
      }
    }
    else {
      compilerModuleExt.setCompilerOutputPath(ideaModuleDir + File.separator + "ebin");
      compilerModuleExt.setCompilerOutputPathForTests(ideaModuleDir + File.separator + ".eunit");
    }
  }

  private static void addModuleContent(ImportedOtpApp importedOtpApp,
                                       ModifiableRootModel rootModel) {
    VirtualFile ideaModuleDir = importedOtpApp.getRoot();
    // Make it inherit SDK from the project.
    rootModel.inheritSdk();
    // Initialize source and test paths.
    ContentEntry content = rootModel.addContentEntry(importedOtpApp.getRoot());
    // Exclude standard folders
    excludeDirFromContent(content, ideaModuleDir, "doc");
    excludeDirFromContent(content, ideaModuleDir, ".rebar3");
    addSourceDirectories(content, importedOtpApp.getSourcePaths(), false);
    addSourceDirectories(content, importedOtpApp.getTestPaths(), true);
    addIncludeDirectories(content, importedOtpApp);
  }

  private static void addSourceDirectories(ContentEntry content, Set<VirtualFile> sourcePaths, boolean isTestSource) {
    for (VirtualFile sourceDirFile : sourcePaths) {
      content.addSourceFolder(sourceDirFile, isTestSource);
    }
  }

  private static void addErlangFacets(final List<ImportedOtpApp> apps) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (ImportedOtpApp app : apps) {
        Module module = app.getModule();
        if (module == null || !app.getWriteAble()) continue;
        ErlangFacet facet = ErlangFacet.getFacet(module);
        if (facet == null) {
          ErlangFacet.createFacet(module);
          facet = ErlangFacet.getFacet(module);
        }
        if (facet != null) {
          ErlangFacetConfiguration configuration = facet.getConfiguration();
          configuration.setParseTransformsFrom(app.getParseTransforms());
          configuration.setGlobalIncludes(app.getGlobalIncludes());
        }
      }
    });
  }

  @Nullable
  private static Sdk fixProjectSdk(@NotNull Project project) {
    final ProjectRootManagerEx projectRootMgr = ProjectRootManagerEx.getInstanceEx(project);
    Sdk selectedSdk = projectRootMgr.getProjectSdk();
    if (selectedSdk == null || selectedSdk.getSdkType() != ErlangSdkType.getInstance()) {
      final Sdk moreSuitableSdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(ErlangSdkType.getInstance());
      ApplicationManager.getApplication().runWriteAction(() -> projectRootMgr.setProjectSdk(moreSuitableSdk));
      return moreSuitableSdk;
    }
    return selectedSdk;
  }


  private static void addIncludeDirectories(@NotNull ContentEntry content, ImportedOtpApp app) {
    for (VirtualFile includeDirectory : app.getIncludePaths()) {
      ErlangIncludeDirectoryUtil.markAsIncludeDirectory(content, includeDirectory);
    }
  }

  private static void excludeDirFromContent(ContentEntry content, VirtualFile root, String excludeDir) {
    VirtualFile excludeDirFile = root.findChild(excludeDir);
    if (excludeDirFile != null) {
      content.addExcludeFolder(excludeDirFile);
    }
  }


  private boolean isExamplesDirectory(VirtualFile virtualFile) {
    return "examples".equals(virtualFile.getName()) && !myImportExamples;
  }

  @Nullable
  private static ImportedOtpApp createImportedOtpApp(@NotNull VirtualFile appRoot, Boolean isRebar3) {
    VirtualFile appResourceFile = findAppResourceFile(appRoot);
    if (appResourceFile == null) {
      return null;
    }
    return new ImportedOtpApp(appRoot, appResourceFile, isRebar3);
  }

  @Nullable
  private static VirtualFile findAppResourceFile(@NotNull VirtualFile applicationRoot) {
    VirtualFile appResourceFile = null;
    VirtualFile sourceDir = applicationRoot.findChild("src");
    if (sourceDir != null) {
      appResourceFile = findFileByExtension(sourceDir, "app.src");
    }
    if (appResourceFile == null) {
      VirtualFile ebinDir = applicationRoot.findChild("ebin");
      if (ebinDir != null) {
        appResourceFile = findFileByExtension(ebinDir, "app");
      }
    }
    return appResourceFile;
  }

  @Nullable
  private static VirtualFile findFileByExtension(@NotNull VirtualFile dir, @NotNull String extension) {
    for (VirtualFile file : dir.getChildren()) {
      if (!file.isDirectory() && file.getName().endsWith(extension)) return file;
    }
    return null;
  }

  private static boolean findIdeaModuleFiles(@NotNull List<ImportedOtpApp> importedOtpApps) {
    boolean ideaModuleFileExists = false;
    for (ImportedOtpApp importedOtpApp : importedOtpApps) {
      VirtualFile applicationRoot = importedOtpApp.getRoot();
      String ideaModuleName = importedOtpApp.getName();
      VirtualFile imlFile = applicationRoot.findChild(ideaModuleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      if (imlFile != null) {
        ideaModuleFileExists = true;
        importedOtpApp.setIdeaModuleFilePath(imlFile.getPath());
      }
      else {
        VirtualFile emlFile = applicationRoot.findChild(ideaModuleName + ".eml");
        if (emlFile != null) {
          ideaModuleFileExists = true;
          importedOtpApp.setIdeaModuleFilePath(emlFile.getPath());
        }
      }
    }
    return ideaModuleFileExists;
  }

  @NotNull
  private static Set<String> resolveModuleDeps(@NotNull ModifiableRootModel rootModel,
                                               @NotNull ImportedOtpApp importedOtpApp,
                                               @Nullable Sdk projectSdk,
                                               @NotNull Set<String> allProjectAppNames) {
    HashSet<String> unresolvedAppNames = new HashSet<>();
    for (String depAppName : importedOtpApp.getDeps()) {
      if (depAppName.equals(rootModel.getModule().getName())) {
        Messages.showWarningDialog(String.format("module %s deps contains it self! check you app or app.src", depAppName), "Rebar Import");
        continue;
      }
      if (allProjectAppNames.contains(depAppName)) {
        rootModel.addInvalidModuleEntry(depAppName);
      }
      else if (projectSdk == null || !isSdkOtpApp(depAppName, projectSdk)) {
        rootModel.addInvalidModuleEntry(depAppName);
        unresolvedAppNames.add(depAppName);
      }
    }
    return unresolvedAppNames;
  }

  private static boolean isSdkOtpApp(@NotNull String otpAppName, @NotNull Sdk sdk) {
    Pattern appDirNamePattern = Pattern.compile(otpAppName + "-.*");
    for (VirtualFile srcSdkDir : sdk.getRootProvider().getFiles(OrderRootType.SOURCES)) {
      if (!srcSdkDir.isValid()) continue;
      for (VirtualFile child : srcSdkDir.getChildren()) {
        if (child.isDirectory() && appDirNamePattern.matcher(child.getName()).find()) {
          return true;
        }
      }
    }
    return false;
  }

  public void setImportExamples(boolean importExamples) {
    myImportExamples = importExamples;
  }

  public void setRebarPath(@NotNull String rebarPath) {
    myRebarPath = rebarPath;
  }

  public void setIsImportingProject(boolean isImportingProject) {
    myIsImportingProject = isImportingProject;
  }

  @Override
  public void setFileToImport(String path) {
    super.setFileToImport(path);
    if (rootStep != null)
      rootStep.setProjectFileDirectory(path);
  }

  public void setRootStep(RebarProjectRootStep rootStep) {
    this.rootStep = rootStep;
  }
}