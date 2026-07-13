# Walkthrough - Reliable Data Synchronization (Restore vs Merge)

I have resolved the data divergence issue between Android and Linux by implementing a more flexible import mechanism that allows users to force a full restoration of the database from a backup file.

## Changes Made

### Persistence Layer Enhancements

#### [AssetDataSource.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/AssetDataSource.kt)
Updated the `importBackup` interface to support a `force` parameter.

#### [ManualAssetDatabase.kt](file:///home/elias/AndroidStudioProjects/B3Check/app/src/main/java/com/example/b3check/ManualAssetDatabase.kt) & [DesktopDataSource.kt](file:///home/elias/AndroidStudioProjects/B3Check/desktop/src/desktopMain/kotlin/DesktopDataSource.kt)
Implemented the forced import logic:
- When `force = true`, the local database/file is cleared before importing the backup data. This ensures absolute parity with the backup file, ignoring any local timestamps that might have blocked the update previously.

### UI Improvements

#### [B3CheckUI.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/B3CheckUI.kt)
Added a confirmation dialog when importing a backup. Users can now choose between:
- **Mesclar (Merge)**: Keeps the most recent version of each asset based on the `lastUpdated` timestamp (default behavior).
- **Restaurar Tudo (Restore)**: Completely replaces local data with the backup file contents.

## Verification Results

### Manual Verification Recommendation
1. **Export from Android**: Generate a backup file.
2. **Modify Linux**: Change a price or score on Linux manually (this creates a newer local timestamp).
3. **Import on Linux**: Select the Android backup.
4. **Choose "Restaurar Tudo"**: Verify that the Linux values are now exactly identical to the Android backup, even if they were "older".
5. **Verify Parade**: Check the "Investir" tab totals on both devices; they should now match exactly.
