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

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.project.Project;
import com.twelvemonkeys.lang.StringUtil;

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
  public static Template createVarDefinitionTemplate(Project project, String varName, boolean varBefore, String equalString){
    ArrayList<String> recommendVarNames = new ArrayList<>();
    recommendVarNames.add(makeErlangVarName(varName));
    return createVarDefinitionTemplate(project, recommendVarNames, varBefore, equalString);
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

}
