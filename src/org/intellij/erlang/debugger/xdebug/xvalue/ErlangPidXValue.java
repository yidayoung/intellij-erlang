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

import com.ericsson.otp.erlang.OtpErlangPid;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.intellij.erlang.debugger.xdebug.ErlangSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ErlangPidXValue extends ErlangXValueBase<OtpErlangPid> {
  public ErlangPidXValue(OtpErlangPid value, String name, XDebugSession session) {
    super(value, name, 3, session);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    XValueChildrenList childrenList = new XValueChildrenList(3);
    addNamedChild(childrenList, getValue().node(), "node", getSession());
    addNamedChild(childrenList, getValue().id(), "id", getSession());
    addNamedChild(childrenList, getValue().serial(), "serial", getSession());
    node.addChildren(childrenList, true);
  }
}
