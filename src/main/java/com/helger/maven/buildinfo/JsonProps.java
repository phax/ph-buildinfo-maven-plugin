package com.helger.maven.buildinfo;

import java.util.Map;

import javax.annotation.Nonnull;

import com.helger.commons.annotation.ReturnsMutableCopy;
import com.helger.commons.collection.ext.CommonsLinkedHashMap;
import com.helger.commons.collection.ext.ICommonsOrderedMap;
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
    IJson aChild = get (sName);
    if (aChild == null)
    {
      aChild = new JsonProps ();
      add (sName, aChild);
    }
    return (JsonProps) aChild;
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
        aTarget.put (sPrefix + "count", Integer.toString (aArray.getSize ()));
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
