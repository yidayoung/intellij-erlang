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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashMap;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class ErlangFileAtomIndex extends FileBasedIndexExtension<String, List<String>> {
  private static final ID<String, List<String>> INDEX = ID.create("erlang.file_atom.index");
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
      Set<String> atoms = new HashSet<>();
      PsiFile file = inputData.getPsiFile();
      if (file instanceof ErlangFile) {
        if (file.getFileType() == ErlangFileType.MODULE) {
          file.accept(new ErlangRecursiveVisitor() {
            @Override
            public void visitQAtom(@NotNull ErlangQAtom o) {
              if (ErlangPsiImplUtil.standaloneAtom(o)) atoms.add(o.getText());
            }
          });
        }
        if (file.getFileType() == ErlangFileType.TERMS){
          file.accept(new ErlangRecursiveVisitor(){
            @Override
            public void visitTupleExpression(@NotNull ErlangTupleExpression o) {
              List<ErlangExpression> expressions = o.getExpressionList();
              ErlangExpression configExpression = expressions.size() >= 2 ? expressions.get(0) : null;
              PsiElement nameQAtom = configExpression instanceof ErlangConfigExpression ? configExpression.getFirstChild() : null;
              ErlangAtom atom = nameQAtom instanceof ErlangQAtom ? ((ErlangQAtom) nameQAtom).getAtom() : null;
              if(atom != null) atoms.add(atom.getName());
            }
          });
        }
        result.put(file.getName(), new ArrayList<>(atoms));
      }
      return result;
    };
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
    return ErlangIndexUtil.ERLANG_ALL_FILTER;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }


  public static List<String> getFileAtoms(Project project, String fileName){
    List<List<String>> values = FileBasedIndex.getInstance().getValues(INDEX, fileName, GlobalSearchScope.allScope(project));
    ArrayList<String> results = new ArrayList<>();
    for (List<String> v : values){
      ContainerUtil.addAllNotNull(results, v);
    }
    return results;
  }
}
