package io.github.artptr.moonexplorer.settings

/** Sets all settings to the default values. The light project is the same for all tests. Thus call this function in setUp and in tearDown. */
fun MoonSettings.resetForTests() = loadState(MoonSettings.SettingsState())
