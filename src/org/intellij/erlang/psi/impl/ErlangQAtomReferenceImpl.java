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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.index.ErlangTypeMapsFieldIndex;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.utils.ErlangTermFileUtil;
import org.intellij.erlang.utils.ErlangVarUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.*;

public class ErlangQAtomReferenceImpl extends ErlangQAtomBasedReferenceImpl {

  private final boolean inFunctionName;

  public ErlangQAtomReferenceImpl(@NotNull PsiElement owner,
                                  ErlangQAtom qAtom,
                                  TextRange range) {
    super(owner, qAtom, range, ErlangPsiImplUtil.getNameIdentifier(qAtom).getText());
    inFunctionName = inFunctionName(qAtom);
  }

  @Nullable
  @Override
  protected PsiElement resolveInner() {
    String configModule = getConfigGetModule(myElement);
    Project project = myElement.getProject();
    if (configModule != null) {
      if (configModule.equals("maps")) {
        return getMapsResolve();
      }
      else {
        PsiElement key = getConfigKeyResolve(configModule, project);
        if (key != null) return key;
      }
    }
    String varName = ErlangVarUtil.getMapsVarName(myElement);
    if (varName != null) {
      return getResolve(project, varName);
    }
    if (inFunctionName){
      PsiElement parent = myElement.getParent().getParent();
      if (parent instanceof ErlangFunction) return parent;
    }
    return null;
  }

  @Nullable
  private PsiElement getMapsResolve() {
    ErlangArgumentList argumentList = PsiTreeUtil.getParentOfType(myElement, ErlangArgumentList.class);
    if (argumentList == null) {
      return null;
    }
    List<ErlangExpression> expressionList = argumentList.getExpressionList();
    if (expressionList.size() >= 2) {
      String varName = ErlangVarUtil.getMapsVarName(expressionList.get(1));
      if (varName != null)
        return getResolve(myElement.getProject(), varName);
    }
    return null;
  }

  @Nullable
  private PsiElement getConfigKeyResolve(String configModule, Project project) {
    PsiFile[] configFiles = FilenameIndex.getFilesByName(project, configModule + ".config", GlobalSearchScope.allScope(project));
    if (configFiles.length == 0) configFiles = FilenameIndex.getFilesByName(project, configModule.replace("data_", "") + ".config", GlobalSearchScope.allScope(project));
    if (configFiles.length == 0) return null;
    PsiFile configFile = configFiles[0];
    List<ErlangTupleExpression> configSections = ErlangTermFileUtil.getConfigSections(configFile, myElement.getText());
    if (configSections.size() > 0) return getQAtom(configSections.get(0));
    ErlangTupleExpression tupleExpression = PsiTreeUtil.getParentOfType(myElement, ErlangTupleExpression.class, true, ErlangArgumentDefinition.class);
    if (tupleExpression == null || !(configFile instanceof ErlangFile)) {
      return configFile.getFirstChild();
    }
    Collection<PsiElement> configKeys = ((ErlangFile) configFile).getConfigKeys();
    if (PsiTreeUtil.getChildOfType(tupleExpression, ErlangQVar.class) == null) {
      // if key dos't contain var, try search same tuple
      String tupleKeyText = tupleExpression.getText();
      PsiElement directSameResolve = ContainerUtil.find(configKeys, key -> StringUtil.equalsIgnoreWhitespaces(key.getText(), tupleKeyText));
      if (null != directSameResolve) return directSameResolve;
    }
    String tupleName = ErlangTermFileUtil.getConfigKeyName(tupleExpression);
    for (PsiElement key : configKeys) {
      if (key instanceof ErlangTupleExpression) {
        if (StringUtil.equalsIgnoreWhitespaces(tupleName, ErlangTermFileUtil.getConfigKeyName(key)))
          return key;
      }
    }
    return configFile.getFirstChild();
  }

  @Nullable
  private PsiElement getResolve(Project project, String mapsName) {

    ErlangFile erlangFile = ErlangTypeMapsFieldIndex.getContainFile(project, mapsName);
    if (erlangFile == null) {
      return null;
    }
    List<ErlangMacrosDefinition> macroses = erlangFile.getMacroses();
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
    if (StringUtil.endsWith(name, "_t")) {
      name = name.substring(0, name.length() - 2);
    }
    return ErlangTypeMapsFieldIndex.getAtomType(name);
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

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (inFunctionName && element instanceof ErlangFunction){
      return !((ErlangFunction)element).getName().equals(myReferenceName);
    }
    return false;
  }
}
