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

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.erlang.ErlangTypes;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErlangGenerateTypeIntention extends ErlangBaseNamedElementIntention {
  protected ErlangGenerateTypeIntention() {
    super("Generate type", "Generate type");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.getManager().isInProject(file)) return false;
    ErlangMacrosDefinition macro = findMacro(file, editor.getCaretModel().getOffset());
    if (macro != null && file instanceof ErlangFile) {
      ErlangArgumentDefinitionList argumentDefinitionList = macro.getArgumentDefinitionList();
      return argumentDefinitionList == null;
    }
    return false;
  }

  @Nullable
  private static ErlangMacrosDefinition findMacro(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    ErlangMacrosName macrosName = PsiTreeUtil.getParentOfType(element, ErlangMacrosName.class, true, ErlangFile.class);
    if (macrosName == null) return null;
    PsiElement parent = macrosName.getParent();
    if (parent instanceof ErlangMacrosDefinition){
      return (ErlangMacrosDefinition)parent;
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ErlangMacrosDefinition macro = findMacro(file, editor.getCaretModel().getOffset());
    if (macro == null) return;
    String name = macro.getName();
    ErlangMacrosBody macrosBody = macro.getMacrosBody();
    ErlangExpression expression = macrosBody == null ? null : macrosBody.getExpressionList().get(0);
    if (expression == null) return;
    String type = "";
    if (expression instanceof ErlangMapExpression)
      type = "map()";
    if (expression instanceof ErlangStringLiteral)
      type = "string()";
    if (expression instanceof ErlangMaxExpression && expression.getFirstChild().getNode().getElementType() == ErlangTypes.ERL_INTEGER)
      type = "integer()";
    int textOffset = macro.getTextRange().getStartOffset();
    editor.getCaretModel().moveToOffset(textOffset);
    runGenerateTypeTemplate(project, name, type, editor);
  }

  private static void runGenerateTypeTemplate(Project project, String name, String type, Editor editor) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "", "-type $name$ :: $type$.$END$\n");
    template.setToReformat(true);
    MyTextExpressionNode var = new MyTextExpressionNode(name.toLowerCase() + "()");
    template.addVariable("name", var, var, true);
    var = new MyTextExpressionNode(type);
    template.addVariable("type", var, var, true);
    templateManager.startTemplate(editor, template);
  }

  private static class MyTextExpressionNode extends VariableNode {
    public MyTextExpressionNode(@NotNull String name) {
      super(name, null);
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      return new TextResult(getName());
    }
  }
}
