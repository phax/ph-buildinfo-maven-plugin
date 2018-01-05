/**
 * Copyright (C) 2014-2018 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.maven.buildinfo;

import java.util.Map;

import javax.annotation.Nonnull;

import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.impl.CommonsLinkedHashMap;
import com.helger.commons.collection.impl.ICommonsOrderedMap;
import com.helger.commons.string.StringHelper;
import com.helger.json.IJson;
import com.helger.json.IJsonArray;
import com.helger.json.IJsonObject;
import com.helger.json.JsonObject;

final class JsonProps extends JsonObject
{
  @Nonnull
  public JsonProps getChild (@Nonnull final String sName)
  {
    return (JsonProps) computeIfAbsent (sName, k -> new JsonProps ());
  }

  @Nonnull
  public JsonProps getChildren (@Nonnull final String... aNames)
  {
    JsonProps ret = this;
    for (final String sName : aNames)
      ret = ret.getChild (sName);
    return ret;
  }

  private void _getFlat (@Nonnull final IJson aJson,
                         @Nonnull final String sPrefix,
                         @Nonnull final ICommonsOrderedMap <String, String> aTarget)
  {
    if (aJson.isValue ())
    {
      final String sName = StringHelper.trimEnd (sPrefix, ".");
      aTarget.put (sName, aJson.getAsValue ().getAsString ());
    }
    else
      if (aJson.isArray ())
      {
        final IJsonArray aArray = aJson.getAsArray ();
        aTarget.put (sPrefix + "count", Integer.toString (aArray.size ()));
        int nIndex = 0;
        for (final IJson aChild : aArray)
        {
          _getFlat (aChild, sPrefix + nIndex + ".", aTarget);
          ++nIndex;
        }
      }
      else
      {
        final IJsonObject aObj = aJson.getAsObject ();
        for (final Map.Entry <String, IJson> aEntry : aObj)
        {
          _getFlat (aEntry.getValue (), sPrefix + aEntry.getKey () + ".", aTarget);
        }
      }
  }

  @Nonnull
  @ReturnsMutableCopy
  public ICommonsOrderedMap <String, String> getAsFlatList ()
  {
    final ICommonsOrderedMap <String, String> ret = new CommonsLinkedHashMap <> ();
    _getFlat (this, "", ret);
    return ret;
  }
}
