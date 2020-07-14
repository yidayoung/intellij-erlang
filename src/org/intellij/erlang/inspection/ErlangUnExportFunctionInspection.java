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

package org.intellij.erlang.inspection;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.intellij.erlang.bif.ErlangBifTable;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.intellij.erlang.quickfixes.ErlangExportFunctionFix;
import org.jetbrains.annotations.NotNull;

public class ErlangUnExportFunctionInspection extends ErlangInspectionBase {
  @NotNull
  @Override
  protected ErlangVisitor buildErlangVisitor(@NotNull final ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session) {
    return new ErlangVisitor() {
      @Override
      public void visitFunctionCallExpression(@NotNull ErlangFunctionCallExpression call) {
        PsiReference reference = call.getReference();
        PsiElement parent = call.getParent();
        ErlangModuleRef moduleRef = parent instanceof ErlangGlobalFunctionCallExpression ?
                                    ((ErlangGlobalFunctionCallExpression) parent).getModuleRef() : null;
        if (reference instanceof ErlangFunctionReference){
          ErlangFunctionReference functionReference = (ErlangFunctionReference) reference;
          if (moduleRef != null &&
              ErlangBifTable.isBif(ErlangPsiImplUtil.getName(moduleRef.getQAtom()),
                                   functionReference.getName(),
                                   functionReference.getArity()))
            return;
        }
        PsiElement resolve = reference.resolve();
        if (resolve instanceof ErlangFunction) {
          if (((ErlangFunction) resolve).isExported() || moduleRef == null) return;
          ErlangFunction function = (ErlangFunction) reference.resolve();
          LocalQuickFix[] fixes = function != null ? new LocalQuickFix[]{new ErlangExportFunctionFix(function)} : new LocalQuickFix[0];
          String name = call.getName();
          int arity = call.getArgumentList().getExpressionList().size();
          String presentation = ErlangPsiImplUtil.createFunctionPresentation(name, arity);
          registerProblem(holder, call.getNameIdentifier(), "UnExport Function " + presentation, fixes);
        }
      }
    };
  }

}
