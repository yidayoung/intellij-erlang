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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.intellij.erlang.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class ErlangTypeMapsFieldIndex extends FileBasedIndexExtension<String, List<String>> {
  private static final ID<String, List<String>> INDEX = ID.create("erlang.maps_field.index");
  private static final int INDEX_VERSION = 1;
  @NotNull
  @Override
  public ID<String, List<String>> getName() {
    return INDEX;
  }

  @NotNull
  @Override
  public DataIndexer<String, List<String>, FileContent> getIndexer() {
    return inputData -> {
      final Map<String, List<String>> result = new THashMap<>();
      PsiFile file = inputData.getPsiFile();
      if (file instanceof ErlangFile) {
        file.accept(new ErlangRecursiveVisitor() {
          @Override
          public void visitMapTuple(@NotNull ErlangMapTuple o) {
            Set<String> fields = new HashSet<>();
            List<ErlangMapEntry> mapEntryList = o.getMapEntryList();
            boolean typed = false;
            String typeName = "";
            for (ErlangMapEntry entry : mapEntryList) {
              List<ErlangExpression> expressionList = entry.getExpressionList();
              if (expressionList.size() == 2) {
                if (expressionList.get(0).getText().equals("?t")) {
                  typed = true;
                  typeName = getAtomType(expressionList.get(1).getText());
                }
                else fields.add(expressionList.get(0).getText());
              }
            }
            if (typed) result.put(typeName, new ArrayList<>(fields));
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

  @NotNull
  @Override
  public DataExternalizer<List<String>> getValueExternalizer() {
    return new DataExternalizer<List<String>>(){

      @Override
      public void save(@NotNull DataOutput out, List<String> value) throws IOException {
        out.writeInt(value.size());
        for (String info : value) {
          IOUtil.writeUTF(out, info);
        }
      }

      @Override
      public List<String> read(@NotNull DataInput in) throws IOException {
        int size = in.readInt();
        ArrayList<String> infos = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
          infos.add(IOUtil.readUTF(in));
        }
        return infos;
      }
    };

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

  public static List<String> getMapsFields(Project project, String VarName){
    List<List<String>> values = FileBasedIndex.getInstance().getValues(INDEX, getMapsVarType(VarName), GlobalSearchScope.allScope(project));
    ArrayList<String> results = new ArrayList<>();
    for (List<String> v : values){
      ContainerUtil.addAllNotNull(results, v);
    }
    return results;
  }

  @Nullable
  public static VirtualFile getContainFile(Project project, String VarName){
    Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(INDEX, getMapsVarType(VarName), GlobalSearchScope.allScope(project));
    if (files.size() > 0) {
      List<VirtualFile> filesList = new ArrayList<>(files);
      return filesList.get(0);
    }
    return null;
  }

}
