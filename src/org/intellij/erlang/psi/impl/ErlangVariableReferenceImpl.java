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

package org.intellij.erlang.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.*;

public class ErlangVariableReferenceImpl extends ErlangPsiPolyVariantCachingReferenceBase<ErlangQVar> {
  private final boolean inClause;

  public ErlangVariableReferenceImpl(@NotNull ErlangQVar element, TextRange range) {
    super(element, range);
    inClause = getClauseScope(element) != null;
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    ErlangVarProcessor processor = new ErlangVarProcessor(myElement.getText(), myElement);
    ErlangLcExpression lc = PsiTreeUtil.getParentOfType(myElement, ErlangLcExpression.class);
    ErlangCompositeElement place = ObjectUtils.chooseNotNull(lc, myElement);
    ResolveUtil.treeWalkUp(place, processor);
    List<ErlangQVar> result = processor.getAllResults();
    result = filterResult(result);
    if (!result.isEmpty()) return PsiElementResolveResult.createResults(result);

    ErlangModule module = getErlangModule();
    if (module == null) return ResolveResult.EMPTY_ARRAY;
    module.processDeclarations(processor, ResolveState.initial(), module, module);

    List<ErlangQVar> allResults = processor.getAllResults();
    allResults = filterResult(allResults);
    return PsiElementResolveResult.createResults(allResults);
  }

  @Nullable
  @Override
  public PsiElement resolveInner() {
    ResolveResult[] resolveResults = multiResolve(false);
    if (resolveResults.length == 0) return null;
    if (resolveResults.length > 1) Arrays.sort(resolveResults, Comparator.comparingInt(o -> Objects.requireNonNull(o.getElement()).getTextOffset()));
    return resolveResults[0].getElement();
  }

  @NotNull
  private List<ErlangQVar> filterResult(List<ErlangQVar> results) {
    return results.stream().filter(
      element ->
        !inDifferentLc(myElement, element) &&
        !inDifferentFun(myElement, element) &&
        !beforeDefine(myElement, element) &&
        (!inClause|| !inOneExpDifferentClause(myElement, element))
    ).collect(Collectors.toList());
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof ErlangQVar)) return false;
    if (myElement.equals(element)) return false;
    if (inDifferentFun(myElement, element)) return false;
    if (inDifferentFunction(myElement, element)) return false;
    if (inDifferentLc(myElement, element)) return false;
    return true;
  }

  @Nullable
  private ErlangModule getErlangModule() {
    PsiFile file = myElement.getContainingFile();
    ErlangFile erlangFile = file instanceof ErlangFile ? (ErlangFile) file : null;
    return erlangFile != null ? erlangFile.getModule() : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    myElement.replace(ErlangElementFactory.createQVarFromText(myElement.getProject(), newElementName));
    return myElement;
  }
}
