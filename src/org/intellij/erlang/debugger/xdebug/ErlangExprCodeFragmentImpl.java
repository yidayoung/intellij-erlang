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

package org.intellij.erlang.debugger.xdebug;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import org.intellij.erlang.ErlangFileType;
import org.intellij.erlang.ErlangLanguage;
import org.intellij.erlang.psi.impl.ErlangFileImpl;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ErlangExprCodeFragmentImpl extends ErlangFileImpl implements ErlangExprCodeFragment {
  private FileViewProvider myViewProvider;
  private Boolean myPhysical;


  public ErlangExprCodeFragmentImpl(Project project, String name, CharSequence text, Boolean isPhysical, @Nullable PsiElement context) {
    super(PsiManagerEx.getInstanceEx(project).getFileManager().createFileViewProvider(
      new LightVirtualFile(name,
                           ErlangLanguage.INSTANCE,
                           text), isPhysical));
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
    myPhysical = isPhysical;
    getOriginalFile().putUserData(ErlangPsiImplUtil.ERLANG_CODE_FRAGMENT, this);
    getOriginalFile().putUserData(ErlangPsiImplUtil.ERLANG_CODE_FRAGMENT_CONTEXT_BY, context);
  }

  @Override
  public boolean isPhysical() {
    return myPhysical != null ? myPhysical: super.isPhysical();
  }

  @NotNull
  @Override
  public FileViewProvider getViewProvider() {
    return myViewProvider != null ? myViewProvider : super.getViewProvider();
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ErlangFileType.MODULE;
  }

  @Override
  protected ErlangExprCodeFragmentImpl clone() {
    final ErlangExprCodeFragmentImpl clone = (ErlangExprCodeFragmentImpl)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myPhysical = false;
    clone.myOriginalFile = this;
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(
      getName(),
      getLanguage(),
      getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }


}