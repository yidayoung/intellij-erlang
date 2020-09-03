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

package org.intellij.erlang.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.ErlangParserDefinition;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ErlangFoldingBuilder extends FoldingBuilderEx implements DumbAware {
  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!(root instanceof ErlangFile)) return FoldingDescriptor.EMPTY;
    ErlangFile file = (ErlangFile) root;

    final List<FoldingDescriptor> result = new ArrayList<>();
    for (ErlangFunction function : file.getFunctions()) {
      result.add(new FoldingDescriptor(function, function.getTextRange()));
    }

    if (!quick) {
      PsiTreeUtil.processElements(file, element -> {
        if (ErlangParserDefinition.COMMENTS.contains(element.getNode().getElementType()) && element.getTextRange().getLength() > 2) {
          result.add(new FoldingDescriptor(element, element.getTextRange()));
        }
        if (PsiTreeUtil.instanceOf(element,
                                   ErlangCaseExpression.class,
                                   ErlangIfExpression.class,
                                   ErlangFunExpression.class))
          result.add(new FoldingDescriptor(element, element.getTextRange()));
        return true;
      });
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi instanceof ErlangFunction) return ErlangPsiImplUtil.createFunctionPresentation((ErlangFunction) psi) + " -> ...";
    IElementType type = node.getElementType();
    if (ErlangParserDefinition.ERL_COMMENT == type) return "% ...";
    if (ErlangParserDefinition.ERL_FUNCTION_DOC_COMMENT == type) return "%% ...";
    if (ErlangParserDefinition.ERL_MODULE_DOC_COMMENT == type) return "%%% ...";
    if (psi instanceof ErlangCaseExpression) {
      PsiElement of = ((ErlangCaseExpression) psi).getOf();
      if (of == null) return null;
      return psi.getText().substring(0, of.getStartOffsetInParent()) + "...";
    }
    if (psi instanceof ErlangIfExpression) {
      List<ErlangIfClause> ifClauseList = ((ErlangIfExpression) psi).getIfClauseList();
      if (ifClauseList.size() == 0) return null;
      ErlangIfClause erlangIfClause = ifClauseList.get(0);
      if (erlangIfClause == null || erlangIfClause.getGuard() == null) return null;
      return "if " + erlangIfClause.getGuard().getText() + "-> ...";
    }
    if (psi instanceof ErlangFunExpression) {
      ErlangFunClauses funClauses = ((ErlangFunExpression) psi).getFunClauses();
      if (funClauses == null) return null;
      ErlangArgumentDefinitionList argumentDefinitionList = funClauses.getFunClauseList().get(0).getArgumentDefinitionList();
      return "fun" + argumentDefinitionList.getText() + " -> ...";
    }
    return null;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}