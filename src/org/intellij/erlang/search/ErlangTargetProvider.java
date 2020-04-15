/*
 * Copyright 2012-2020 Sergey Ignatov
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

package org.intellij.erlang.search;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.index.ErlangModuleIndex;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.ErlangModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangTargetProvider implements UsageTargetProvider {
  @Nullable
  @Override
  public UsageTarget[] getTargets(@NotNull PsiElement psiElement) {
    if (psiElement instanceof ErlangFile){
      ErlangFile psiFile = (ErlangFile)psiElement;
      FileType fileType = ((ErlangFile) psiElement).getFileType();
      PsiElement findElement = psiElement;
      if (ErlangFileType.MODULE == fileType) {
        findElement = psiFile.getModule();
      }
      if (ErlangFileType.TERMS == fileType){
        String moduleName = psiFile.getVirtualFile().getNameWithoutExtension();
        Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
        assert module != null;
        List<ErlangModule> modulesByName = ErlangModuleIndex.getModulesByName(psiFile.getProject(), moduleName, GlobalSearchScope.moduleWithDependentsScope(module));
        if (modulesByName.size() > 0)
          findElement = modulesByName.get(0);
      }
//      if (ErlangFileType.HEADER == fileType){
//        findElement = ErlangElementFactory.createIncludeString(psiElement.getProject(), psiFile.getName());
//      }
      assert findElement != null;
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(findElement)};
    }

    return new UsageTarget[0];
  }
}
