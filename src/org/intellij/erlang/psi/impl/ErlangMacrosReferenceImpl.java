/*
 * Copyright 2012-2019 Sergey Ignatov
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.psi.ErlangFile;
import org.intellij.erlang.psi.ErlangMacros;
import org.intellij.erlang.psi.ErlangMacrosDefinition;
import org.intellij.erlang.psi.ErlangMacrosName;
import org.jetbrains.annotations.NotNull;

public class ErlangMacrosReferenceImpl extends PsiReferenceBase<PsiElement> {
  private final ErlangMacrosName myNameElement;
  private final String myReferenceName;

  public ErlangMacrosReferenceImpl(@NotNull PsiElement owner, ErlangMacrosName element) {
    super(owner, ErlangPsiImplUtil.getTextRangeForReference(owner, element));
    myNameElement = element;
    myReferenceName = ErlangPsiImplUtil.getNameIdentifier(element).getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    ErlangPsiImplUtil.setName(myNameElement, newElementName);
    return myElement;
  }

  @Override
  public PsiElement resolve() {
    ErlangMacrosDefinition definition = PsiTreeUtil.getParentOfType(myNameElement, ErlangMacrosDefinition.class);
    if (definition != null && definition.getMacrosName() == myNameElement) return null;

    PsiFile containingFile = myElement.getContainingFile();
    if (containingFile instanceof ErlangFile) {
      ErlangMacrosDefinition macros = ((ErlangFile) containingFile).getMacros(myReferenceName);
      if (macros != null) {
        return macros;
      }
      return ContainerUtil.getFirstItem(ErlangPsiImplUtil.getErlangMacrosFromIncludes((ErlangFile) containingFile, false, myReferenceName));
    }
    return null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.toObjectArray(ErlangPsiImplUtil.getMacrosLookupElements(myElement.getContainingFile()));
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(myElement instanceof ErlangMacros)) return false;
    PsiReference reference = myElement.getReference();
    PsiElement resolve = reference == null ? null : reference.resolve();
    if (resolve != null && resolve.equals(element)) return true;
    return false;
  }
}
