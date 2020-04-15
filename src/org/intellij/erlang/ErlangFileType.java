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

package org.intellij.erlang;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public abstract class ErlangFileType extends BaseErlangFileType {
  public static final BaseErlangFileType MODULE = ModuleFileType.INSTANCE;
  public static final BaseErlangFileType HEADER = HrlFileType.INSTANCE;
  public static final BaseErlangFileType APP = AppFileType.INSTANCE;
  public static final BaseErlangFileType TERMS = ErlangTermsFileType.INSTANCE;
  public static final List<BaseErlangFileType> TYPES = ContainerUtil.immutableList(MODULE, HEADER, APP, TERMS);


  private ErlangFileType(
    @NotNull String name,
    @NotNull String description,
    @NotNull Icon icon,
    @NotNull String... extensions) {
    super(name, description, icon, extensions);
  }

}
