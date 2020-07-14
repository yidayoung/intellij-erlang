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
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.intellij.erlang.index.ErlangFileAtomIndex;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ErlangUnresolvedConfigKeyInspection extends ErlangInspectionBase {
  @NotNull
  @Override
  protected ErlangVisitor buildErlangVisitor(@NotNull final ProblemsHolder holder,
                                             @NotNull LocalInspectionToolSession session) {
    return new ErlangVisitor() {
      @Override
      public void visitFunctionCallExpression(@NotNull ErlangFunctionCallExpression call) {
        PsiReference reference = call.getReference();
        if (!(reference instanceof ErlangFunctionReference) || reference.resolve() != null) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof ErlangFile) {
            ErlangArgumentList argumentList = call.getArgumentList();
            List<ErlangExpression> expressionList = argumentList.getExpressionList();
            if (expressionList.size() == 1) {
              PsiElement[] children = expressionList.get(0).getChildren();
              if (children.length == 1) {
                PsiElement arg = children[0];
                if (arg instanceof ErlangQAtom && ErlangPsiImplUtil.standaloneAtom((ErlangQAtom) arg)) {
                  checkConfigKey(holder, (ErlangFile) resolve, (ErlangQAtom) arg);
                }
              }
            }
          }
        }
      }
    };
  }


  private void checkConfigKey(@NotNull ProblemsHolder holder, ErlangFile config, ErlangQAtom atom) {
    List<String> fileAtoms = ErlangFileAtomIndex.getFileAtoms(config, null);
    if (fileAtoms.contains(atom.getText())) return;

    registerProblem(holder, atom, "UnDefined Key: " + atom.getText() + " in config file " + config.getName());
  }

}
