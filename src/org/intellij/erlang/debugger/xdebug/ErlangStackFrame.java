/*
 * Copyright 2012-2014 Sergey Ignatov
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

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.intellij.erlang.debugger.node.ErlangTraceElement;
import org.intellij.erlang.debugger.node.ErlangVariableBinding;
import org.intellij.erlang.debugger.xdebug.xvalue.ErlangXValueFactory;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ErlangPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErlangStackFrame extends XStackFrame {
  private final ErlangXDebugProcess myDebugProcess;
  private final ErlangTraceElement myTraceElement;
  private final ErlangSourcePosition mySourcePosition;

  public ErlangStackFrame(@NotNull ErlangXDebugProcess debugProcess,
                          @NotNull ErlangTraceElement traceElement) {
    this(debugProcess, traceElement, ErlangSourcePosition.create(debugProcess.getLocationResolver(), traceElement));
  }

  public ErlangStackFrame(@NotNull ErlangXDebugProcess debugProcess,
                          @NotNull ErlangTraceElement traceElement,
                          @Nullable ErlangSourcePosition sourcePosition) {
    myDebugProcess = debugProcess;
    myTraceElement = traceElement;
    mySourcePosition = sourcePosition;
  }

  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    return new XDebuggerEvaluator() {
      @Override
      public void evaluate(@NotNull String expression,
                           @NotNull XEvaluationCallback callback,
                           @Nullable XSourcePosition expressionPosition) {
        myDebugProcess.evaluateExpression(expression, callback, myTraceElement, expressionPosition);
      }

      @Nullable
      @Override
      public ExpressionInfo getExpressionInfoAtOffset(@NotNull Project project,
                                                      @NotNull Document document,
                                                      int offset,
                                                      boolean sideEffectsAllowed) {

        return PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
          try {
            PsiElement elementAtCursor = DebuggerUtilsEx.findElementAt(PsiDocumentManager.getInstance(project).getPsiFile(document), offset);
            if (elementAtCursor == null || !elementAtCursor.isValid()) {
              return null;
            }
            if (!(elementAtCursor instanceof ErlangQVar)){
              elementAtCursor = PsiTreeUtil.getParentOfType(elementAtCursor, ErlangExpression.class);
              if (elementAtCursor instanceof ErlangFunctionCallExpression && elementAtCursor.getParent() instanceof ErlangGlobalFunctionCallExpression)
                elementAtCursor = elementAtCursor.getParent();
            }
            if (elementAtCursor == null) return null;
            return new ExpressionInfo(elementAtCursor.getTextRange(), elementAtCursor.getText());
          } catch (IndexNotReadyException ignored) {}
          return null;
        });
      }
    };
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    if (mySourcePosition == null) return null;
    return mySourcePosition.getSourcePosition();
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    String functionName = mySourcePosition != null ? mySourcePosition.getFunctionName() : null;
    if (functionName != null) {
      ErlangFile module = myDebugProcess.getLocationResolver().findPsi(mySourcePosition.getSourcePosition().getFile());
      ErlangFunction function = module != null ? module.getFunction(functionName, mySourcePosition.getFunctionArity()) : null;
      if (function != null) {
        String title = ErlangPsiImplUtil.getQualifiedFunctionName(function);
        ErlangFunExpression funExpression = ErlangPsiImplUtil.findFunExpression(function, mySourcePosition.getFunExpressionArity());
        if (funExpression != null) {
          int line = 1 + StringUtil.offsetToLineNumber(funExpression.getContainingFile().getText(), funExpression.getTextOffset());
          title += ": " + mySourcePosition.getFunExpressionName() + " at line " + line;
        }
        component.append(title, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        component.setIcon(AllIcons.Debugger.Frame);
        return;
      }
    }
    super.customizePresentation(component);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList myVariables = new XValueChildrenList(myTraceElement.getBindings().size());
    for (ErlangVariableBinding binding : myTraceElement.getBindings()) {
      myVariables.add(binding.getName(), getVariableValue(binding));
    }
    node.addChildren(myVariables, true);
  }

  private XValue getVariableValue(ErlangVariableBinding value) {
    return ErlangXValueFactory.create(value.getValue(), value.getName(), myDebugProcess.getSession());
  }
}
