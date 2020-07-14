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

package org.intellij.erlang.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.intellij.erlang.psi.ErlangAtom;
import org.intellij.erlang.psi.ErlangQAtom;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;

public class ErlangAtomSearch extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  protected ErlangAtomSearch() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters parameters,
                           @NotNull Processor<? super PsiReference> consumer) {
    PsiElement element = parameters.getElementToSearch();
    if (!(element instanceof ErlangQAtom)) return;

    String name = ErlangPsiImplUtil.getName((ErlangQAtom) element);
    if (StringUtil.isEmpty(name)) return;

    SearchScope searchScope = parameters.getScopeDeterminedByUser();
    MyCodeOccurenceProcessor processor = new MyCodeOccurenceProcessor(element, consumer);
    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_STRINGS;
    PsiSearchHelper.getInstance(element.getProject()).processElementsWithWord(processor, searchScope, name, searchContext, true);
  }

  private static class MyCodeOccurenceProcessor implements TextOccurenceProcessor {
    private final PsiElement myElement;
    private final Processor<? super PsiReference> myPsiReferenceProcessor;
    private final boolean myIsAloneAtom;

    public MyCodeOccurenceProcessor(@NotNull PsiElement element,
                                    @NotNull Processor<? super PsiReference> psiReferenceProcessor) {
      myElement = element;
      myPsiReferenceProcessor = psiReferenceProcessor;
      if (element instanceof ErlangQAtom) {
        myIsAloneAtom = ErlangPsiImplUtil.standaloneAtom((ErlangQAtom) element);
      }
      else myIsAloneAtom = false;
    }

    public boolean execute(@NotNull PsiElement element, int offsetInElement) {

      if (element instanceof ErlangQAtom && element.getText().equals(myElement.getText())){
        PsiReference reference = element.getReference();
        if (reference!=null){
          if (myIsAloneAtom){
            if (ErlangPsiImplUtil.standaloneAtom((ErlangQAtom) element))
              return myPsiReferenceProcessor.process(reference);
            return true;
          }
          if (reference.isReferenceTo(myElement)) return myPsiReferenceProcessor.process(reference);
        }
      }
      return true;
    }
  }
}
