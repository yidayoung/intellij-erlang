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

package org.intellij.erlang.maps;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "MapsHrlSettings", storages = {
  @Storage(value = "maps_hrl.xml")
})
public class MapsHrlSettings implements PersistentStateComponent<MapsHrlSettings> {

  private List<String> myHrlFiles;
  @NotNull
  public static MapsHrlSettings getInstance(@NotNull Project project) {
    MapsHrlSettings persisted = ServiceManager.getService(project, MapsHrlSettings.class);
    return persisted != null ? persisted : new MapsHrlSettings();
  }
  
  @Nullable
  @Override
  public MapsHrlSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull MapsHrlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }


  public List<String> getHrlFiles() {
    return myHrlFiles;
  }

  public void setHrlFiles(List<String> hrlFiles) {
    myHrlFiles = hrlFiles;
  }

  public String gerAllHrl(){
    return myHrlFiles!=null?StringUtil.join(myHrlFiles, ";"):"";
  }

}
