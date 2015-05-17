# UnifiedPreferences
Unified Shared Preferences for Android. Allows to use DB, JSON as a low level storage for SharedPreferences. Highly improved performance on low end devices.

#Usage

For examples take look into Unit Tests.

```java
public PreferencesUnified getPreferencesUnified() {
  return new PreferencesUnified(getContext(), UNIT_TESTS_PREFS, OrgJsonSerializer.Instance);
}

public SharedPreferences getDbPreferences() {
  return PreferencesToDb.newInstance(getContext(), UNIT_TESTS_DB);
}
```
