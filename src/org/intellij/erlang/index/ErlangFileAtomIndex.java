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

import com.intellij.openapi.diagnostic.Logger;
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
import org.intellij.erlang.jps.builder.ErlangBuilder;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class ErlangFileAtomIndex extends FileBasedIndexExtension<String, Map<String, Integer>> {
  private static final ID<String, Map<String, Integer>> INDEX = ID.create("erlang.file_atom.index");
  private static final int INDEX_VERSION = 1;
  @NotNull
  @Override
  public ID<String, Map<String, Integer>> getName() {
    return INDEX;
  }
  static final Logger LOG = Logger.getInstance(ErlangFileAtomIndex.class);
  @NotNull
  @Override
  public DataIndexer<String, Map<String, Integer>, FileContent> getIndexer() {
    return inputData -> {
      Map<String, Map<String, Integer>> result = new THashMap<>();
      Map<String, Integer> fileAtoms = new THashMap<>();
      PsiFile file = inputData.getPsiFile();
      if (file instanceof ErlangFile) {
        if (file.getFileType() == ErlangFileType.MODULE) {
          file.accept(new ErlangRecursiveVisitor() {
            @Override
            public void visitQAtom(@NotNull ErlangQAtom o) {
              if (ErlangPsiImplUtil.standaloneAtom(o)){
                Integer old = fileAtoms.get(o.getText()) == null ? 0:fileAtoms.get(o.getText());
                fileAtoms.put(o.getText(), old+1);
              }
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
              if(atom != null) {
                Integer old = fileAtoms.get(atom.getName()) == null ? 0:fileAtoms.get(atom.getName());
                fileAtoms.put(atom.getText(), old+1);
              }
            }
          });
        }
        result.put(file.getName(), fileAtoms);
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
  public DataExternalizer<Map<String, Integer>> getValueExternalizer() {
    return new DataExternalizer<Map<String, Integer>>(){
      @Override
      public void save(@NotNull DataOutput out, Map<String, Integer> value) throws IOException {
        out.writeInt(value.size());
        value.forEach((s, integer) -> {
          try {
            IOUtil.writeUTF(out, s);
            IOUtil.writeUTF(out, integer.toString());
          }
          catch (IOException e) {
            LOG.error("ErlangFileAtom save err", e);
          }
        });
      }

      @Override
      public Map<String, Integer> read(@NotNull DataInput in) throws IOException {
        int size = in.readInt();
        Map<String, Integer> value = new HashMap<>();
        for (int i = 0; i < size; i++) {
          String key = IOUtil.readUTF(in);
          Integer count = Integer.parseInt(IOUtil.readUTF(in));
          value.put(key, count);
        }
        return value;
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
    return getFileAtoms(project, fileName, null);
  }

  public static List<String> getFileAtoms(Project project, String fileName, @Nullable String exceptIfOnceOccurAtom){
    List<Map<String, Integer>> values = FileBasedIndex.getInstance().getValues(INDEX, fileName, GlobalSearchScope.allScope(project));
    ArrayList<String> results = new ArrayList<>();
    for (Map<String, Integer> v : values){
      Set<String> atoms = v.keySet();
      Integer count = v.get(exceptIfOnceOccurAtom);
      if(count != null && count == 1)
      {
        atoms.remove(exceptIfOnceOccurAtom);
      }
      ContainerUtil.addAllNotNull(results, atoms);
    }
    return results;
  }
}
