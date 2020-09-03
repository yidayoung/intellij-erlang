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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.erlang.psi.ErlangRecordDefinition;
import org.intellij.erlang.psi.ErlangTypedExpr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErlangRecordDocProvider implements ElementDocProvider  {
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
    if (myPsiElement instanceof ErlangRecordDefinition)
      return generateRecordDoc((ErlangRecordDefinition)myPsiElement);
    if (myPsiElement instanceof ErlangTypedExpr)
      return generateRecordFieldDoc((ErlangTypedExpr)myPsiElement);
    return null;
  }

  @Nullable
  private static String generateRecordFieldDoc(ErlangTypedExpr fieldType) {
    PsiElement parent = fieldType.getParent().getParent();
    if (!(parent instanceof ErlangRecordDefinition)) return null;
    ErlangRecordDefinition recordDefinition = (ErlangRecordDefinition) parent;
    String name = recordDefinition.getName();
    if (isProtoRecord(name)) {
      String recordFieldDoc = generateRecordFieldDoc(recordDefinition, fieldType);
      if (recordFieldDoc != null) return recordFieldDoc;
    }
    String doc = fieldType.getText();
    PsiComment comment = PsiTreeUtil.getNextSiblingOfType(fieldType, PsiComment.class);
    if (comment != null)
      doc += "    " + ErlangDocUtil.getCommentText(comment);
    doc += ("\n\n" + parent.getText());
    return ErlangDocUtil.wrapInPreTag(doc);

  }

  @Nullable
  private static String generateRecordFieldDoc(ErlangRecordDefinition recordDefinition, ErlangTypedExpr fieldType) {
    String recordName = recordDefinition.getName();
    PsiFile protoFile = findProtoFile(recordName, recordDefinition.getProject());
    if (protoFile == null) return null;
    return findDocInProtoFile(recordName, protoFile, fieldType.getName());
  }

  @Nullable
  private static String findDocInProtoFile(String recordName, PsiFile protoFile, String name) {
    PsiElement message = findMessage(recordName, protoFile);
    if (message != null){
      PsiElement[] fields = message.getLastChild().getChildren();
      for (PsiElement field : fields){
        PsiElement[] children = field.getChildren();
        String result = field.getText();
        PsiComment comment = getFieldComment(field);
        if (children.length > 2) {
          PsiElement filedName = children[1].getNextSibling().getNextSibling();
          if (filedName.getText().equals(name)) {
            if (comment != null)
              result += comment.getText();
            result += "\n\n" + message.getText();
            return ErlangDocUtil.wrapInPreTag(result);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiComment getFieldComment(PsiElement field) {
    if (field.getNextSibling() instanceof PsiComment)
      return (PsiComment) field.getNextSibling();
    field = field.getNextSibling();
    if (field.getNextSibling() instanceof PsiComment)
      return (PsiComment) field.getNextSibling();
    return null;
  }

  @NotNull
  private static String generateRecordDoc(ErlangRecordDefinition recordDefinition) {
    String name = recordDefinition.getName();
    if (isProtoRecord(name)){
      String docTextFromProto = getDocTextFromProto(name, recordDefinition.getProject());
      if (docTextFromProto != null) return docTextFromProto;
    }
    return ErlangDocUtil.wrapInPreTag(recordDefinition.getText());
  }

  private static boolean isProtoRecord(String name) {
    return name.startsWith("p_") || name.startsWith("cs_") || name.startsWith("sc_");
  }

  @Nullable
  private static String getDocTextFromProto(String name, Project project) {
    PsiFile protoFile = findProtoFile(name, project);
    return protoFile != null ? findDocInProtoFile(name, protoFile) : null;
  }

  @Nullable
  private static String findDocInProtoFile(String name, PsiFile protoFile) {
    PsiElement child = findMessage(name, protoFile);
    if (child != null) return ErlangDocUtil.wrapInPreTag(child.getText());
    return null;
  }

  @Nullable
  private static PsiElement findMessage(String name, PsiFile protoFile) {
    PsiElement[] children = protoFile.getChildren();
    for (PsiElement child : children){
      if (isProtoMessage(child)){
        String messageName = child.getFirstChild().getNextSibling().getNextSibling().getText();
        if (messageName.equals(name)) return child;
      }
    }
    return null;
  }

  private static boolean isProtoMessage(PsiElement psiElement){
    PsiElement firstChild = psiElement.getFirstChild();
    if (firstChild == null) return false;
    return firstChild.getText().equals("message");
  }

  @Nullable
  private static PsiFile findProtoFile(String name, Project project) {
    String moduleName = moduleName(name);
    PsiFile[] protoFiles = FilenameIndex.getFilesByName(project, moduleName + ".proto", GlobalSearchScope.allScope(project));
    if (protoFiles.length > 0) return protoFiles[0];
    protoFiles = FilenameIndex.getFilesByName(project, "common.proto", GlobalSearchScope.allScope(project));
    if (protoFiles.length > 0) return protoFiles[0];
    return null;
  }

  private static String moduleName(String name) {
    List<String> strings = StringUtil.split(name, "_");
    if (strings.size() > 2)
      return strings.get(1);
    return name;
  }
}
