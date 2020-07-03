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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
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
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class ErlangFileAtomIndex extends FileBasedIndexExtension<String, ErlangFileAtomIndex.ErlangFileAtoms> {
  private static final ID<String, ErlangFileAtoms> INDEX = ID.create("erlang.file_atom.index");
  private static final int INDEX_VERSION = 1;
  @NotNull
  @Override
  public ID<String, ErlangFileAtoms> getName() {
    return INDEX;
  }
  private static final Logger LOG = Logger.getInstance(ErlangFileAtomIndex.class);

  public static class ErlangFileAtoms{
    public ErlangFileAtoms(Set<String> onceAtoms, Set<String> moreThanOnceAtoms) {
      myOnceAtoms = onceAtoms;
      myMoreThanOnceAtoms = moreThanOnceAtoms;
    }

    public Set<String> getOnceAtoms() {
      return myOnceAtoms;
    }

    private Set<String> myOnceAtoms;

    public Set<String> getMoreThanOnceAtoms() {
      return myMoreThanOnceAtoms;
    }

    private Set<String> myMoreThanOnceAtoms;
    public ErlangFileAtoms() {
      this.myOnceAtoms = new TreeSet<>();
      this.myMoreThanOnceAtoms = new TreeSet<>();
    }
    public void insert(String atomName){
      if (myMoreThanOnceAtoms.contains(atomName)) return;
      if (myOnceAtoms.contains(atomName)){
        myMoreThanOnceAtoms.add(atomName);
        myOnceAtoms.remove(atomName);
      }
      myOnceAtoms.add(atomName);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof ErlangFileAtoms)) return false;
      ErlangFileAtoms obj2 = (ErlangFileAtoms) obj;
      if (!(myOnceAtoms.equals(obj2.myOnceAtoms))) return false;
      if (!(myMoreThanOnceAtoms.equals(obj2.myMoreThanOnceAtoms))) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
  @NotNull
  @Override
  public DataIndexer<String, ErlangFileAtoms, FileContent> getIndexer() {
    return inputData -> {
      Map<String, ErlangFileAtoms> result = new THashMap<>();
      ErlangFileAtoms fileAtoms = new ErlangFileAtoms();
      PsiFile file = inputData.getPsiFile();
      if (file instanceof ErlangFile) {
        if (file.getFileType() == ErlangFileType.MODULE) {
          file.accept(new ErlangRecursiveVisitor() {
            @Override
            public void visitQAtom(@NotNull ErlangQAtom o) {
              if (ErlangPsiImplUtil.standaloneAtom(o) && o.getAtom() != null){
                fileAtoms.insert(o.getAtom().getName());
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
                fileAtoms.insert(atom.getName());
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
  public DataExternalizer<ErlangFileAtoms> getValueExternalizer() {
    return new DataExternalizer<ErlangFileAtoms>(){
      private void writeStringSet(@NotNull DataOutput out, Set<String> set) throws IOException {
        out.writeInt(set.size());
        for (String s : set){
          IOUtil.writeUTF(out, s);
        }
      }

      private Set<String> readStringSet(@NotNull DataInput in) throws IOException {
        int size = in.readInt();
        Set<String> strings = new TreeSet<>();
        for (int i = 0; i < size; i++) {
          String key = IOUtil.readUTF(in);
          strings.add(key);
        }
        return strings;
      }

      @Override
      public void save(@NotNull DataOutput out, ErlangFileAtoms value) throws IOException {
        Set<String> moreThanOnceAtoms = value.getMoreThanOnceAtoms();
        writeStringSet(out, moreThanOnceAtoms);
        Set<String> onceAtoms = value.getOnceAtoms();
        writeStringSet(out, onceAtoms);
      }

      @Override
      public ErlangFileAtoms read(@NotNull DataInput in) throws IOException {
        Set<String> moreThanOnceAtoms = readStringSet(in);
        Set<String> onceAtoms = readStringSet(in);
        return new ErlangFileAtoms(onceAtoms, moreThanOnceAtoms);
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
    PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.projectScope(project));
    ArrayList<String> results = new ArrayList<>();
    for (PsiFile file : files){
      ContainerUtil.addAllNotNull(results, getFileAtoms(file, null));
    }
    return results;
  }
  public static List<String> getFileAtoms(PsiFile file, @Nullable String exceptIfOnceOccurAtom){
    Module module = ModuleUtilCore.findModuleForFile(file.getOriginalFile());
    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : ProjectScope.getProjectScope(file.getProject());
    List<ErlangFileAtoms> values = FileBasedIndex.getInstance().getValues(INDEX, file.getName(), scope);
    ArrayList<String> results = new ArrayList<>();
    for (ErlangFileAtoms v : values){
      ContainerUtil.addAllNotNull(results, v.getMoreThanOnceAtoms());
      Set<String> atoms = v.getOnceAtoms();
      if (exceptIfOnceOccurAtom !=null) atoms.remove(exceptIfOnceOccurAtom);
      ContainerUtil.addAllNotNull(results, atoms);
    }
    return results;
  }
}
