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

import com.ericsson.otp.erlang.OtpErlangMap;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValueChildrenList;

public class ErlangMapXValue extends ErlangArrayXValueBase<OtpErlangMap> {
  public ErlangMapXValue(OtpErlangMap value, String name, XDebugSession session) {
    super(value, name, value.arity(), session);
  }

  @Override
  protected void computeChild(XValueChildrenList children, int childIdx) {
    OtpErlangObject key = getValue().keys()[childIdx];
    OtpErlangObject value = getValue().get(key);
    addIndexedChild(children, new ErlangMappingXValue(key, value, getName(), getSession()), childIdx);
  }
}
