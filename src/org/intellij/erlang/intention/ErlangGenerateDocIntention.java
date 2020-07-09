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

package org.intellij.erlang.intention;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.quickfixes.ErlangGenerateSpecFix;
import org.intellij.erlang.types.ErlangExpressionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangGenerateDocIntention extends ErlangBaseNamedElementIntention{
  protected ErlangGenerateDocIntention() {
    super("Generate Doc", "Generate Doc");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof ErlangFile)) return false;
    ErlangFunction function = findFunction(file, editor.getCaretModel().getOffset());
    if (function == null) return false;
    return function.findSpecification() == null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!(file instanceof ErlangFile)) {
      throw new IncorrectOperationException("Only applicable to Erlang files.");
    }
    ErlangFunction function = findFunction(file, editor.getCaretModel().getOffset());
    if (function == null) {
      throw new IncorrectOperationException("Cursor should be placed on Erlang function.");
    }
    int textOffset = function.getTextOffset();
    Template template = createErlangDocTemplate(project, function);
    editor.getCaretModel().moveToOffset(textOffset);
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  private static Template createErlangDocTemplate(Project project, ErlangFunction function) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("");
    template.addTextSegment("%%------------------------------------------------------------------------------\n");
    template.addTextSegment("%% @spec ");
    addSpec(template, function);
    template.addTextSegment("\n%% @doc ");
//    template.addVariable(new ConstantNode(""), true);
    template.addEndVariable();
    template.addTextSegment("\n%% @end\n" +
                  "%%------------------------------------------------------------------------------\n");
    return template;
  }

  private static void addSpec(Template template, ErlangFunction function) {
    int arity = function.getArity();
    List<ErlangArgumentDefinition> argumentDefinitionList =
      function.getFunctionClauseList().get(0).getArgumentDefinitionList().getArgumentDefinitionList();
    template.addTextSegment(function.getName()+"(");
    for (int i = 0; i < arity; i++) {
      ErlangExpression expression = argumentDefinitionList.get(i).getExpression();
      template.addTextSegment(ErlangGenerateSpecFix.getArgName(expression, i)+"::");
      ErlangExpressionType erlangExpressionType = ErlangExpressionType.create(expression);
      String typeString = ErlangGenerateSpecFix.getTypeString(erlangExpressionType);
      template.addVariable(new ConstantNode(typeString), true);
      if (i < arity - 1)
        template.addTextSegment(", ");
    }
    template.addTextSegment(") -> ");
    String typeString = ErlangGenerateSpecFix.getTypeString(ErlangGenerateSpecFix.computeReturnType(function));
    template.addVariable(new ConstantNode(typeString), true);
  }

  @Nullable
  private static ErlangFunction findFunction(PsiFile file, int offset) {
    return findElement(file, offset, ErlangFunction.class);
  }

}
