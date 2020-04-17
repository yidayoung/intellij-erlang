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

package org.intellij.erlang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.erlang.icons.ErlangIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class BaseErlangFileType extends LanguageFileType {
  private final String myName;
  private final String myDescription;
  private final Icon myIcon;
  private final List<String> myExtensions;

  public BaseErlangFileType(
    @NotNull String name,
    @NotNull String description,
    @NotNull Icon icon,
    @NotNull String... extensions) {
    super(ErlangLanguage.INSTANCE);

    myName = name;
    myDescription = description;
    myIcon = icon;
    myExtensions = ContainerUtil.immutableList(extensions);
  }

  @NotNull
  @Override
  public final String getName() {
    return myName;
  }

  @NotNull
  @Override
  public final String getDescription() {
    return myDescription;
  }

  @Nullable
  @Override
  public final Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public final String getDefaultExtension() {
    return myExtensions.get(0);
  }

  @NotNull
  public final List<String> getDefaultExtensions() {
    return myExtensions;
  }

  public static class ModuleFileType extends BaseErlangFileType {
    public static final ModuleFileType INSTANCE = new ModuleFileType();
    private ModuleFileType() {
      super("Erlang",
            "Erlang",
            ErlangIcons.FILE,
            "erl");
    }
  }

  public static class HrlFileType extends BaseErlangFileType {
    public static final HrlFileType INSTANCE = new HrlFileType();
    private HrlFileType() {
      super("Erlang Header",
            "Erlang/OTP Header File",
            ErlangIcons.HEADER,
            "hrl");
    }
  }

  public static class AppFileType extends BaseErlangFileType {
    private static final String APP = "app";
    private static final String APP_SRC = "src";
    public static final AppFileType INSTANCE = new AppFileType();
    private AppFileType() {
      super("Erlang/OTP app",
            "Erlang/OTP Application Resource File",
            ErlangIcons.OTP_APP_RESOURCE,
            APP,
            APP_SRC);
    }


  }

  public static class ErlangTermsFileType extends BaseErlangFileType {
    public static final ErlangTermsFileType INSTANCE = new ErlangTermsFileType();
    private ErlangTermsFileType() {
      super("Erlang Terms",
            "Erlang Terms File",
            ErlangIcons.TERMS,
            "config",
            "routes",
            "rel");
    }
  }
  
}
