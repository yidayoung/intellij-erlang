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

package org.intellij.erlang.debugger.xdebug.xvalue;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import org.intellij.erlang.icons.ErlangIcons;
import org.intellij.erlang.psi.*;
import org.intellij.erlang.psi.impl.ResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;

class ErlangXValueBase<T extends OtpErlangObject> extends XValue {
  private final T myValue;
  private final int myChildrenCount;
  private final String myName;
  private int myNextChildIdxToCompute;
  private final XDebugSession mySession;

  protected ErlangXValueBase(T value, String name, XDebugSession session) {
    this(value, name, 0, session);
  }

  protected ErlangXValueBase(T value, String name, int childrenCount, XDebugSession session) {
    myValue = value;
    myChildrenCount = childrenCount;
    mySession = session;
    myName = name;
  }

  protected T getValue() {
    return myValue;
  }

  public XDebugSession getSession() {
    return mySession;
  }

  public String getName() {
    return myName;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    int nextToLastChildIdx = Math.min(myNextChildIdxToCompute + XCompositeNode.MAX_CHILDREN_TO_SHOW, myChildrenCount);
    XValueChildrenList children = new XValueChildrenList(nextToLastChildIdx - myNextChildIdxToCompute);
    for (int i = myNextChildIdxToCompute; i < nextToLastChildIdx; i++) {
      computeChild(children, i);
    }
    myNextChildIdxToCompute = nextToLastChildIdx;
    boolean computedAllChildren = myNextChildIdxToCompute == myChildrenCount;
    if (!computedAllChildren) {
      node.tooManyChildren(myChildrenCount - myNextChildIdxToCompute);
    }
    node.addChildren(children, computedAllChildren);
  }

  @Override
  public final void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    XValuePresentation presentation = getPresentation(node, place);
    if (presentation != null) {
      node.setPresentation(getIcon(), presentation, hasChildren());
    }
    else {
      String repr = getStringRepr();
      if (repr.length() > XValueNode.MAX_VALUE_LENGTH) {
        node.setFullValueEvaluator(new ImmediateFullValueEvaluator(repr));
        repr = repr.substring(0, XValueNode.MAX_VALUE_LENGTH - 3) + "...";
      }
      node.setPresentation(getIcon(), getType(), repr, hasChildren());
    }
  }


  @Override
  public void computeSourcePosition(@NotNull XNavigatable xNavigatable) {
    XSourcePosition position = mySession.getCurrentPosition();
    if (position == null) return;
    VirtualFile file = position.getFile();
    Project project = mySession.getProject();
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (psiFile != null && editor instanceof TextEditor){
      PsiElement element = psiFile.findElementAt(((TextEditor) editor).getEditor().getDocument().getLineStartOffset(position.getLine()));
      ResolveUtil.treeWalkUp(element, (e, state) -> {
        if (e instanceof ErlangQVar && e.getText().equals(myName)) {
          PsiElement parent = e.getParent().getParent();
          if (PsiTreeUtil.instanceOf(parent, ErlangAssignmentExpression.class, ErlangArgumentDefinition.class)) {
            SourcePosition position1 = SourcePosition.createFromElement(parent);
            xNavigatable.setSourcePosition(DebuggerUtilsEx.toXSourcePosition(position1));
            return false;
          }
        }
        if (e instanceof ErlangFile)
          return false;
        return true;
      });
    }
  }

  @NotNull
  @Override
  public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
    computeSourcePosition(callback::computed);
    return ThreeState.YES;
  }

  @NotNull
  @Override
  public Promise<XExpression> calculateEvaluationExpression() {
    return super.calculateEvaluationExpression();
  }

  protected void computeChild(XValueChildrenList children, int childIdx) {
  }

  @Nullable
  protected XValuePresentation getPresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    return null;
  }

  @Nullable
  protected String getType() {
    return null;
  }

  @NotNull
  protected String getStringRepr() {
    return myValue.toString();
  }

  protected Icon getIcon() {
    return ErlangIcons.DEBUGGER_VALUE;
  }

  private boolean hasChildren() {
    return myChildrenCount != 0;
  }

  protected void addIndexedChild(XValueChildrenList childrenList, long numericChild, int childIdx, XDebugSession session) {
    addIndexedChild(childrenList, new OtpErlangLong(numericChild), childIdx, session);
  }

  protected void addIndexedChild(XValueChildrenList childrenList, OtpErlangObject child, int childIdx, XDebugSession session) {
    addIndexedChild(childrenList, ErlangXValueFactory.create(child, myName, session), childIdx);
  }

  protected static void addIndexedChild(XValueChildrenList childrenList, XValue child, int childIdx) {
    addNamedChild(childrenList, child, "[" + (childIdx + 1) + "]");
  }

  protected void addNamedChild(XValueChildrenList childrenList, long numericChild, String name, XDebugSession session) {
    addNamedChild(childrenList, new OtpErlangLong(numericChild), name, session);
  }

  protected void addNamedChild(XValueChildrenList childrenList, String atomicChild, String name, XDebugSession session) {
    addNamedChild(childrenList, new OtpErlangAtom(atomicChild), name, session);
  }

  protected void addNamedChild(XValueChildrenList childrenList, OtpErlangObject child, String name, XDebugSession session) {
    addNamedChild(childrenList, ErlangXValueFactory.create(child, myName, session), name);
  }

  private static void addNamedChild(XValueChildrenList childrenList, XValue child, String name) {
    childrenList.add(name, child);
  }
}

class ErlangPrimitiveXValueBase<T extends OtpErlangObject> extends ErlangXValueBase<T> {
  public ErlangPrimitiveXValueBase(T value, String name, XDebugSession session) {
    super(value, name, session);
  }

  @Override
  protected Icon getIcon() {
    return ErlangIcons.DEBUGGER_PRIMITIVE_VALUE;
  }
}

class ErlangArrayXValueBase<T extends OtpErlangObject> extends ErlangXValueBase<T> {
  protected ErlangArrayXValueBase(T value, String name, int childrenCount, XDebugSession session) {
    super(value, name, childrenCount, session);
  }

  @Override
  protected Icon getIcon() {
    return ErlangIcons.DEBUGGER_ARRAY;
  }
}