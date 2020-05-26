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

package org.intellij.erlang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.index.ErlangTypeMapsFieldIndex;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.rebar.util.ErlangTermFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.getConfigGetModule;
import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.getMapsVarName;

public class ErlangQAtomReferenceImpl extends ErlangQAtomBasedReferenceImpl {

  public ErlangQAtomReferenceImpl(@NotNull PsiElement owner,
                                  ErlangQAtom qAtom,
                                  TextRange range) {
    super(owner, qAtom, range, ErlangPsiImplUtil.getNameIdentifier(qAtom).getText());
  }

  @Nullable
  @Override
  protected PsiElement resolveInner() {
    String configModule = getConfigGetModule(myElement);
    Project project = myElement.getProject();
    if (configModule != null) {
      if (configModule.equals("maps")) {
        ErlangArgumentList argumentList = PsiTreeUtil.getParentOfType(myElement, ErlangArgumentList.class);
        if (argumentList == null) {
          return null;
        }
        List<ErlangExpression> expressionList = argumentList.getExpressionList();
        if (expressionList.size() >= 2) {
          String varName = expressionList.get(1).getText();
          return getResolve(project, varName);
        }

      }
      else {
        PsiFile[] configFiles = FilenameIndex.getFilesByName(project, configModule + ".config", GlobalSearchScope.allScope(project));
        if (configFiles.length > 0) {
          PsiFile configFile = configFiles[0];
          List<ErlangTupleExpression> configSections = ErlangTermFileUtil.getConfigSections(configFile, myElement.getText());
          return getQAtom(configSections.get(0));
        }
      }
    }
    String varName = getMapsVarName(myElement);
    if (varName != null) {
      return getResolve(project, varName);
    }
    return null;
  }

  @Nullable
  private PsiElement getResolve(Project project, String mapsName) {

    VirtualFile containFile = ErlangTypeMapsFieldIndex.getContainFile(project, mapsName);
    if (containFile == null) {
      return null;
    }
    PsiFile psiFile = PsiManager.getInstance(project).findFile(containFile);
    if (psiFile == null) {
      return null;
    }
    List<ErlangMacrosDefinition> macroses = ((ErlangFile) psiFile).getMacroses();
    String mapsTypeMacroName = ErlangTypeMapsFieldIndex.getMapsVarType(mapsName);
    for (ErlangMacrosDefinition macro : macroses) {
      if (getMacroType(macro.getName()).equals(mapsTypeMacroName)) {
        ErlangMacrosBody macrosBody = macro.getMacrosBody();
        if (macrosBody == null) {
          return null;
        }
        ErlangMapTuple mapTuple = PsiTreeUtil.findChildOfType(macrosBody, ErlangMapTuple.class);
        assert mapTuple != null;
        List<ErlangMapEntry> mapEntryList = mapTuple.getMapEntryList();
        for (ErlangMapEntry entry : mapEntryList) {
          List<ErlangExpression> expressionList = entry.getExpressionList();
          if (expressionList.size() == 2) {
            PsiElement qAtom = getQAtom(expressionList.get(0));
            if (qAtom != null && qAtom.getText().equals(myElement.getText())) {
              return qAtom;
            }
          }
        }
      }
    }
    return null;
  }

  private static String getMacroType(String name) {
    if (StringUtil.endsWith(name,"_t")){
     return name.substring(0, name.length()-2).toLowerCase().replaceAll("[^a-z]", "");
    }
    return "";
  }

  @Nullable
  private PsiElement getQAtom(PsiElement psiElement) {
    ErlangQAtom qAtom = PsiTreeUtil.findChildOfType(psiElement, ErlangQAtom.class);
    if (qAtom != null) {
      String field = qAtom.getText();
      if (field.equals(myElement.getText())) {
        return qAtom;
      }
    }
    return null;
  }
}
