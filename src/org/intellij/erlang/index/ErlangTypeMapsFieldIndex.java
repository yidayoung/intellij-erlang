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

package org.intellij.erlang.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ErlangTypeMapsFieldIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> INDEX = ID.create("erlang.maps_field.index");
  private static final int INDEX_VERSION = 1;
  @NotNull
  @Override
  public ID<String, Void> getName() {
    return INDEX;
  }

  @NotNull
  @Override
  public DataIndexer<String,Void, FileContent> getIndexer() {
    return inputData -> {
      final Map<String, Void> result = new THashMap<>();
      PsiFile file = inputData.getPsiFile();
      if (file instanceof ErlangFile) {
        file.accept(new ErlangRecursiveVisitor() {
          @Override
          public void visitMacrosDefinition(@NotNull ErlangMacrosDefinition o) {
            ErlangMacrosName macrosName = o.getMacrosName();
            if (macrosName != null && macrosName.getText().endsWith("_t"))
            {
              ErlangMacrosBody macrosBody = o.getMacrosBody();
              if (macrosBody != null && macrosBody.getExpressionList().get(0) instanceof ErlangMapExpression)
                result.put(getAtomType(macrosName.getText().substring(0, macrosName.getText().length() - 2)), null);
            }
          }
        });
      }
      return result;
    };
  }

  public static String getMapsVarType(String text) {
    List<String> split = StringUtil.split(text, "_");
    if (split.size() > 1){
      text = split.get(0);
    }
    return getAtomType(text);
  }

  public static String getAtomType(String text){
    return text.toLowerCase().replaceAll("[^a-z]", "");
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }



  @Override
  public int getVersion() {
    return INDEX_VERSION;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return ErlangIndexUtil.ERLANG_HRL_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Nullable
  public static ErlangFile getContainFile(Project project, String varName){
    Collection<VirtualFile> containingFiles = FileBasedIndex.getInstance().getContainingFiles(INDEX, getMapsVarType(varName), GlobalSearchScope.allScope(project));
    if (containingFiles.size() > 0) {
      PsiFile file = PsiManager.getInstance(project).findFile(containingFiles.iterator().next());
      if (file instanceof ErlangFile)
      return (ErlangFile) file;
    }
    return null;
  }

}
