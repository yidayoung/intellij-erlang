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

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.psi.ErlangRecordDefinition;
import org.intellij.erlang.psi.ErlangTypedExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangRecordDocProvider implements ElementDocProvider {
  private final PsiElement myPsiElement;

  public ErlangRecordDocProvider(PsiElement psiElement) {
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
    if (myPsiElement instanceof ErlangRecordDefinition) {
      return generateRecordDoc((ErlangRecordDefinition) myPsiElement);
    }
    if (myPsiElement instanceof ErlangTypedExpr) {
      return generateRecordFieldDoc((ErlangTypedExpr) myPsiElement);
    }
    return null;
  }

  @Nullable
  private static String generateRecordFieldDoc(ErlangTypedExpr fieldType) {
    PsiElement parent = fieldType.getParent().getParent();
    if (!(parent instanceof ErlangRecordDefinition)) return null;
    String doc = fieldType.getText();
    PsiComment comment = PsiTreeUtil.getNextSiblingOfType(fieldType, PsiComment.class);
    if (comment != null) {
      doc += "    " + ErlangDocUtil.getCommentText(comment);
    }
    doc += ("\n\n" + parent.getText());
    return ErlangDocUtil.wrapInPreTag(doc);

  }

  @NotNull
  private static String generateRecordDoc(ErlangRecordDefinition recordDefinition) {
    return ErlangDocUtil.wrapInPreTag(recordDefinition.getText());
  }
}
