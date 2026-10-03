# Converter development directory

See the [project README](../README.md) for requirements, folder selection, recovery instructions, limitations and troubleshooting.

From this directory, with a Java 21 JDK configured:

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat desktop --console=plain
.\gradlew.bat sourceRelease --console=plain
```

The desktop reads `config/defaults.json` for initial coordinate bounds. Source folders are selected in the application. Verified working copies and prepared job settings are stored under the current user's `VoxyRecovery/jobs` folder, outside the repository.

Private research fixtures and local job configurations are optional and are not distributed. Public integration tests create their own synthetic Voxy database.
