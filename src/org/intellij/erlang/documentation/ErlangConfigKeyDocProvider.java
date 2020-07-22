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

package org.intellij.erlang.documentation;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.psi.ErlangTupleExpression;
import org.intellij.erlang.rebar.util.ErlangTermFileUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangConfigKeyDocProvider implements ElementDocProvider {

  private final PsiFile myPsiFile;
  private final PsiElement myConfigKey;

  public ErlangConfigKeyDocProvider(VirtualFile file, PsiElement configKey) {
    myPsiFile = PsiManager.getInstance(configKey.getProject()).findFile(file);
    myConfigKey = configKey;
  }

  @Nullable
  @Override
  public List<String> getExternalDocUrls() {
    return null;
  }

  @Nullable
  @Override
  public String getDocText() {
    List<ErlangTupleExpression> configSections = ErlangTermFileUtil.getConfigSections(myPsiFile, myConfigKey.getText());
    if (configSections.size() > 0) {
      ErlangTupleExpression configTuple = configSections.get(0);
      PsiComment comment = PsiTreeUtil.getPrevSiblingOfType(configTuple, PsiComment.class);
      if (comment != null) {
        String result = configTuple.getText() + "\n" + ErlangDocUtil.getCommentsText(
          ErlangDocUtil.collectPrevComments(comment), "%%", ErlangDocUtil.EDOC_FUNCTION_TAGS);
        return ErlangDocUtil.wrapInPreTag(result);
      }
    }
    return null;
  }
}
