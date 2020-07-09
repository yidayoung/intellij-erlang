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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.psi.ErlangMapEntry;
import org.intellij.erlang.psi.ErlangMapTuple;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangMapsKeyDocProvider implements ElementDocProvider {
  private final PsiElement myPsiElement;

  public ErlangMapsKeyDocProvider(PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Nullable
  @Override
  public List<String> getExternalDocUrls() {
    return null;
  }

  @Nullable
  @Override
  public String getDocText() {
    ErlangMapTuple mapTuple = PsiTreeUtil.getParentOfType(myPsiElement, ErlangMapTuple.class);
    if (mapTuple == null) {
      return null;
    }
    ErlangMapEntry entry = PsiTreeUtil.getParentOfType(myPsiElement, ErlangMapEntry.class);
    PsiComment comment = PsiTreeUtil.getNextSiblingOfType(entry, PsiComment.class);
    String docString = StringUtil.convertLineSeparators(mapTuple.getText(), "<br>");
    if (comment != null){
      docString = entry.getText() + "    " + comment.getText() + "<br><br>" + docString;
    }
    return docString;
  }
}
