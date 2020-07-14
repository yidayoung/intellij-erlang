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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.twelvemonkeys.lang.StringUtil;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ErlangIntroduceValueIntention extends ErlangBaseNamedElementIntention{
  protected ErlangIntroduceValueIntention() {
    super("introduce val", "introduce val");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.getManager().isInProject(file)) return false;
    if (file.getFileType() != ErlangFileType.MODULE) return false;
    ErlangExpression expression = findExpression(file, editor.getCaretModel().getOffset());
    if (expression != null && file instanceof ErlangFile) {
      return true;
    }
    return false;
  }

  @Nullable
  private static ErlangExpression findExpression(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiElement next = element.getPrevSibling();
    while (next != null && !(next instanceof PsiWhiteSpace)){
      if (next instanceof ErlangExpression) return (ErlangExpression)next;
      next = next.getPrevSibling();
    }
    ErlangExpression expression = PsiTreeUtil.getParentOfType(element, ErlangExpression.class);
    if (expression != null && expression.getTextOffset() == offset){
      return expression;
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!(file instanceof ErlangFile)) {
      throw new IncorrectOperationException("Only applicable to Erlang files.");
    }
    ErlangExpression expression = findExpression(file, editor.getCaretModel().getOffset());
    if (expression == null) {
      throw new IncorrectOperationException("Cursor should be placed on Erlang expression end.");
    }
    String funName = null;
    String configKey = null;
    if (expression instanceof ErlangGlobalFunctionCallExpression) {
      funName = ((ErlangGlobalFunctionCallExpression) expression).getFunctionCallExpression().getNameIdentifier().getText();
      ErlangArgumentList argumentList = ((ErlangGlobalFunctionCallExpression) expression).getFunctionCallExpression().getArgumentList();
      ErlangQAtom qAtom = PsiTreeUtil.findChildOfType(argumentList, ErlangQAtom.class);
      if (qAtom != null) configKey = qAtom.getText();
    }
    if (expression instanceof ErlangFunctionCallExpression) {
      funName = ((ErlangFunctionCallExpression) expression).getNameIdentifier().getText();
    }
    ArrayList<String> recommendVarNames = new ArrayList<>();
    if (funName != null){
      if (funName.equals("get")){
        if (configKey!=null && configKey.length() > 1 && Character.isLowerCase(configKey.charAt(0))){
          recommendVarNames.add(makeErlangVarName(configKey));
        }
        recommendVarNames.add("Val");
      }
      addCommonName(funName, recommendVarNames);
      recommendVarNames.add(makeErlangVarName(funName));
      recommendVarNames.add("Result");
    }
    int textOffset = expression.getTextOffset();
    Template template = createVarDefinitionTemplate(project, recommendVarNames);
    editor.getCaretModel().moveToOffset(textOffset);
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }

  private static void addCommonName(String name, ArrayList<String> recommendVarNames) {
    while (name.contains("_")){
      name = name.substring(name.indexOf("_")+1);
      recommendVarNames.add(makeErlangVarName(name));
    }
  }

  private static String makeErlangVarName(String string) {
    StringBuilder varName = new StringBuilder();
    if (string.contains("_")){
      String[] splits = string.split("_");
      for (String s : splits){
        String s1 = s.toLowerCase();
        if (s1.length() > 1){
          s1 = s1.substring(0, 1).toUpperCase().concat(s1.substring(1));
        }
        varName.append(s1);
      }
    }
    else varName.append(string);
    if (varName.length() > 1)
      varName.replace(0, 1, StringUtil.toUpperCase(varName.substring(0,1)));
    return varName.toString();
  }

  private static Template createVarDefinitionTemplate(Project project, ArrayList<String> varName) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);
    if (varName.isEmpty()) varName.add("V");
    template.addVariable("var_name", new ConstantNode(varName.get(0)).withLookupStrings(varName), true);
    template.addTextSegment(" = ");
    template.addEndVariable();
    return template;
  }
}
