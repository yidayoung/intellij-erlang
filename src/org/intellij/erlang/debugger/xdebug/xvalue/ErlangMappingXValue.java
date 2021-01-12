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

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.intellij.erlang.debugger.xdebug.ErlangSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ErlangMappingXValue extends ErlangXValueBase<OtpErlangTuple> {
  public ErlangMappingXValue(OtpErlangObject key, OtpErlangObject value, String name, XDebugSession session) {
    super(new OtpErlangTuple(new OtpErlangObject[]{key, value}), name, 2, session);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList children = new XValueChildrenList(2);
    addNamedChild(children, getMappingKey(), "key", getSession());
    addNamedChild(children, getMappingValue(), "value", getSession());
    node.addChildren(children, true);
  }

  @Nullable
  @Override
  protected String getType() {
    return "Mapping";
  }

  @NotNull
  @Override
  protected String getStringRepr() {
    return getMappingKey() + " => " + getMappingValue();
  }

  private OtpErlangObject getMappingKey() {
    return getValue().elementAt(0);
  }

  private OtpErlangObject getMappingValue() {
    return getValue().elementAt(1);
  }
}
