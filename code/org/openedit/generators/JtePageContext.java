package org.openedit.generators;

import java.util.Map;

/**
 * Wraps the OpenEdit page map (from
 * {@link org.openedit.WebPageRequest#getPageMap()})
 * for use as a single typed {@code @param} in JTE templates.
 *
 * <p>
 * Templates should declare:
 * 
 * <pre>
 *   {@literal @}param org.openedit.generators.JtePageContext context
 * </pre>
 * 
 * and access values via {@code ${context.get("key")}} or the typed convenience
 * getters that will be added here as templates are migrated.
 */
public class JtePageContext {
  private final Map<String, Object> fieldPageMap;

  public JtePageContext(Map<String, Object> inPageMap) {
    fieldPageMap = inPageMap;
  }

  /**
   * Returns the value for the given key, or {@code null} if absent.
   */
  public Object get(String inKey) {
    return fieldPageMap.get(inKey);
  }

  /**
   * Returns the value for the given key as a {@link String}, or {@code null}.
   */
  public String getString(String inKey) {
    Object val = fieldPageMap.get(inKey);
    return val == null ? null : val.toString();
  }

  /**
   * Returns {@code true} when the named value is non-null and, if it is a
   * {@link String}, non-empty.
   */
  public boolean has(String inKey) {
    Object val = fieldPageMap.get(inKey);
    if (val == null) {
      return false;
    }
    if (val instanceof String) {
      return !((String) val).isEmpty();
    }
    return true;
  }

  /**
   * Direct access to the underlying map for templates that need to iterate
   * over all context entries.
   */
  public Map<String, Object> getAll() {
    return fieldPageMap;
  }
}
