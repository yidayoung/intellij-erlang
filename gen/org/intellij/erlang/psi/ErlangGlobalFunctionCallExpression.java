// This is a generated file. Not intended for manual editing.
package org.intellij.erlang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ErlangGlobalFunctionCallExpression extends ErlangExpression {

  @Nullable
  ErlangFunctionCallExpression getFunctionCallExpression();

  @NotNull
  ErlangModuleRef getModuleRef();

  @NotNull
  PsiElement getColon();

}
