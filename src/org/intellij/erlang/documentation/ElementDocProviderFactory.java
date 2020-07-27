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

package org.intellij.erlang.documentation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.bif.ErlangBifTable;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ElementDocProviderFactory {
  private ElementDocProviderFactory() {
  }

  @Nullable
  static ElementDocProvider create(@NotNull PsiElement psiElement) {
    Project project = psiElement.getProject();
    if (psiElement instanceof ErlangModule) {
      VirtualFile virtualFile = getVirtualFile(psiElement);
      if (virtualFile == null) return null;
      ErlangModule erlangModule = (ErlangModule) psiElement;
      if (isFileFromErlangSdk(project, virtualFile)) {
        return new ErlangSdkModuleDocProvider(project, virtualFile);
      }
      return new ErlangModuleDocProvider(erlangModule);
    }
    else if (psiElement instanceof ErlangFunction) {
      VirtualFile virtualFile = getVirtualFile(psiElement);
      if (virtualFile == null) return null;
      ErlangFunction erlangFunction = (ErlangFunction) psiElement;
      if (isFileFromErlangSdk(project, virtualFile)) {
        return new ErlangSdkFunctionDocProvider(project, erlangFunction.getName(), erlangFunction.getArity(), virtualFile);
      }
      return new ErlangFunctionDocProvider(erlangFunction);
    }
    else if (psiElement instanceof ErlangTypeDefinition) {
      VirtualFile virtualFile = getVirtualFile(psiElement);
      if (virtualFile == null) return null;
      ErlangTypeDefinition typeDefinition = (ErlangTypeDefinition) psiElement;
      if (isFileFromErlangSdk(project, virtualFile)) {
        return new ErlangSdkTypeDocProvider(project, virtualFile, typeDefinition.getName());
      }
      return null; // TODO implement TypeDocProvider
    }
    else if (psiElement instanceof ErlangRecordDefinition || psiElement instanceof ErlangTypedExpr){
      return new ErlangRecordDocProvider(psiElement);
    }
    else {
      VirtualFile virtualFile = getVirtualFile(psiElement);
      if (virtualFile != null){
        if (virtualFile.getFileType() == ErlangFileType.TERMS){
          return new ErlangConfigKeyDocProvider(virtualFile, psiElement);
        }
        if (virtualFile.getFileType() == ErlangFileType.HEADER){
          return new ErlangMapsKeyDocProvider(psiElement);
        }
      }
      ErlangGlobalFunctionCallExpression erlGlobalFunctionCall = PsiTreeUtil.getParentOfType(
        psiElement, ErlangGlobalFunctionCallExpression.class);
      if (erlGlobalFunctionCall != null) {
        ErlangModuleRef moduleRef = erlGlobalFunctionCall.getModuleRef();
        String moduleName = moduleRef.getText();
        ErlangFunctionCallExpression erlFunctionCall = erlGlobalFunctionCall.getFunctionCallExpression();
        String functionName = erlFunctionCall.getName();
        int arity = erlFunctionCall.getArgumentList().getExpressionList().size();
        if (ErlangBifTable.isBif(moduleName, functionName, arity)) {
          PsiElement tentativeErlangModule = moduleRef.getReference().resolve();
          if (tentativeErlangModule instanceof ErlangModule) {
            if (virtualFile != null) {
              return new ErlangSdkFunctionDocProvider(project, functionName, arity, virtualFile);
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isFileFromErlangSdk(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    Sdk projectSdk = projectRootManager.getProjectSdk();
    if (projectSdk == null) return false;
    for (VirtualFile sdkSourceRoot : projectSdk.getRootProvider().getFiles(OrderRootType.SOURCES)) {
      if (virtualFile.getPath().startsWith(sdkSourceRoot.getPath())) return true;
    }
    return false;
  }

  @Nullable
  private static VirtualFile getVirtualFile(@NotNull PsiElement psiElement) {
    PsiFile containingFile = psiElement.getContainingFile();
    return containingFile == null ? null : containingFile.getVirtualFile();
  }
}
