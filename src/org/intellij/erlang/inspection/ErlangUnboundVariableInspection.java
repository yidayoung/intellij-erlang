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

package org.intellij.erlang.inspection;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiUtilBase;
import org.intellij.erlang.psi.ErlangClauseBody;
import org.intellij.erlang.psi.ErlangQVar;
import org.intellij.erlang.psi.ErlangVisitor;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.intellij.erlang.quickfixes.ErlangQuickFixBase;
import org.jetbrains.annotations.NotNull;

import static org.intellij.erlang.psi.impl.ErlangPsiImplUtil.*;

public class ErlangUnboundVariableInspection extends ErlangInspectionBase {
  @NotNull
  @Override
  protected ErlangVisitor buildErlangVisitor(@NotNull final ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
    return new ErlangVisitor() {
      @Override
      public void visitQVar(@NotNull ErlangQVar o) {
        if (inArgumentDefinition(o) && !inArgumentList(o)
          || inDefinitionBeforeArgumentList(o)
          || inLeftPartOfAssignment(o) || inAtomAttribute(o)
          || isForceSkipped(o) || inSpecification(o) || inDefine(o)
          || inCallback(o) || inRecordDefinition(o) || inConsoleFile(o))  {
          return;
        }

        PsiReference reference = o.getReference();
        if (reference.resolve() == null) {
          registerProblem(holder, o, "Variable " + "'" + o.getText() + "' is unbound", new ErlangIntroduceVariableQuickFix());
        }
        else {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof ErlangQVar && getClauseScope(resolve) != null)
          {
            if (findValInClauseButNotDefine(resolve, o) == null) return;
            registerProblem(holder, o, "Variable " + "'" + o.getText() + "' is bound in case, but not all branch bound", new ErlangIntroduceVariableInCaseQuickFix());
          }
        }
      }
    };
  }

  private static class ErlangIntroduceVariableQuickFix extends ErlangQuickFixBase {
    @NotNull
    @Override
    public String getFamilyName() {
      return "Introduce variable";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (!(psiElement instanceof ErlangQVar)) return;

      PsiElement anchor = psiElement;
      while (anchor != null && !(anchor.getParent() instanceof ErlangClauseBody)) {
        anchor = anchor.getParent();
      }

      if (anchor != null) {
        Editor editor = PsiUtilBase.findEditor(anchor);
        if (editor == null) return;

        editor.getCaretModel().moveToOffset(anchor.getTextRange().getStartOffset());

        TemplateManager manager = TemplateManager.getInstance(project);
        Template template = manager.createTemplate("", "");

        template.addTextSegment(((ErlangQVar) psiElement).getName());
        template.addTextSegment(" = ");
        template.addVariable(new ConstantNode("unbound"), true);
        template.addTextSegment("");
        template.addEndVariable();
        template.addTextSegment(",\n");

        manager.startTemplate(editor, template);

      }
    }
  }

  private static class ErlangIntroduceVariableInCaseQuickFix extends ErlangQuickFixBase {
    @NotNull
    @Override
    public String getFamilyName() {
      return "Introduce variable";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (!(psiElement instanceof ErlangQVar)) return;
      PsiReference reference = psiElement.getReference();
      PsiElement resolve = reference == null ? null:reference.resolve();
      if (resolve ==null) return;
      PsiElement anchor = findValInClauseButNotDefine(resolve, psiElement);
      PsiElement parent = anchor == null?null:anchor.getParent();
      if (anchor != null && parent != null) {
        Editor editor = PsiUtilBase.findEditor(anchor);
        if (editor == null) return;
        String body = anchor.getText();
        if (body.substring(body.length() - 1).equals(";")) {
          editor.getCaretModel().moveToOffset(anchor.getTextRange().getEndOffset() - 1);
        }
        else {
          editor.getCaretModel().moveToOffset(anchor.getTextRange().getEndOffset());
        }

        TemplateManager manager = TemplateManager.getInstance(project);
        Template template = manager.createTemplate("", "");
        template.addTextSegment(",\n");
        template.addTextSegment(((ErlangQVar) psiElement).getName());
        template.addTextSegment(" = ");
        template.addVariable(new ConstantNode("unbound"), true);
        template.addTextSegment("");
        template.addEndVariable();

        manager.startTemplate(editor, template);
      }
    }
  }
}
