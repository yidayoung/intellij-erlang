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

package org.intellij.erlang.utils;

import com.intellij.codeInsight.completion.BasicInsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.twelvemonkeys.lang.StringUtil;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ErlangVarUtil {

  private ErlangVarUtil() {
  }

  public static String makeErlangVarName(String string) {
    StringBuilder varName = new StringBuilder();
    if (string.contains("_")){
      String[] splits = string.split("_");
      for (String s : splits){
        String s1 = s.toLowerCase();
        if (s1.length() > 1){
          s1 = StringUtil.capitalize(s1);
        }
        varName.append(s1);
      }
    }
    else varName.append(string);
    if (varName.length() > 1)
      varName = new StringBuilder(StringUtil.capitalize(varName.toString()));
    return varName.toString();
  }

  public static Template createVarDefinitionTemplate(Project project, ArrayList<String> varName, boolean varBefore, String equalString) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);
    if (varName.isEmpty()) varName.add("V");
    if (varBefore){
      template.addVariable("var_name", new ConstantNode(varName.get(0)).withLookupStrings(varName), true);
      template.addTextSegment(equalString + " ");
    }
    else {
      template.addTextSegment(equalString + " ");
      template.addVariable("var_name", new ConstantNode(varName.get(0)).withLookupStrings(varName), true);
    }
    template.addEndVariable();
    return template;
  }

  @Nullable
  public static String getMapsVarName(PsiElement psiElement){
    ErlangMapExpression atomMapExpression = getAtomMapExpression(psiElement);
    if (atomMapExpression != null) {
      // Box#{b....}
      String mapsVarNameBefore = getMapsVarNameBefore(atomMapExpression);
      return mapsVarNameBefore != null? mapsVarNameBefore:getMapsVarNameAfter(atomMapExpression);
    }
    return null;
  }

  @Nullable
  public static String getMapsVarNameBefore(ErlangMapExpression atomMapExpression){
    ErlangExpression expression = PsiTreeUtil.getChildOfType(atomMapExpression, ErlangMaxExpression.class);
    String valName = expression != null ? expression.getText() : null;
    if (valName != null && valName.startsWith("?") && valName.endsWith("_t"))
      valName = valName.substring(1, valName.length()-2).replace("_", "");
    return valName;
  }

  @Nullable
  public static String getMapsVarNameAfter(ErlangMapExpression atomMapExpression){
    ErlangExpression expression = PsiTreeUtil.getChildOfType(atomMapExpression.getParent(), ErlangMaxExpression.class);
    return expression != null ? expression.getText(): null;
  }

  @Nullable
  public static ErlangMapExpression getAtomMapExpression(PsiElement psiElement){
    PsiElement parent = psiElement.getParent();
    if (!(parent instanceof ErlangAtom || parent instanceof ErlangMapTuple || psiElement instanceof ErlangQAtom)){
      return null;
    }
    ErlangMapTuple tuple = PsiTreeUtil.getParentOfType(psiElement, ErlangMapTuple.class, true,
                                                       ErlangFunctionCallExpression.class, ErlangFunClause.class, ErlangMapExpression.class);
    PsiElement result = tuple != null ? tuple.getParent() : null;
    return result instanceof ErlangMapExpression ? (ErlangMapExpression)result:null;
  }
  public static class ErlangFieldInsertHandle extends BasicInsertHandler<LookupElement>{
    private final Project myProject;
    private final ArrayList<String> myVarName;
    private final boolean myVarBefore;
    private final String myEqualString;

    public ErlangFieldInsertHandle(Project project, String varName, boolean varBefore, String equalString) {
      myProject = project;
      myVarName = new ArrayList<>();
      myVarName.add(makeErlangVarName(varName));
      myVarBefore = varBefore;
      myEqualString = equalString;
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      super.handleInsert(context, item);
      Editor editor = context.getEditor();
      Template template = createVarDefinitionTemplate(myProject, myVarName, myVarBefore, myEqualString);
      TemplateManager.getInstance(myProject).startTemplate(editor, template);
    }
  }
}

