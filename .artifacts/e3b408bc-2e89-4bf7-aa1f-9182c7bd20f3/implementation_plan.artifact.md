# Implementation Plan - Fix Data Consistency and Export/Import Coherence

Resolve the data loss issue during asset updates and ensure the export/import mechanisms correctly handle share counts, values, and metadata.

## User Review Required

> [!IMPORTANT]
> **Critical Bug Identified**: I found that editing values (like share counts or prices) currently wipes out all associated metadata (Pros, Cons, Neutros, and Analysis Sources) because the current update mechanism does not preserve fields that are not in the class constructor. I will implement a safe update pattern to fix this.

> [!NOTE]
> **Market Score Persistence**: The market score researched via AI was not updating the "last modified" timestamp, which could cause it to be ignored during backup imports if a local version existed. I will fix this to ensure all AI-researched data is correctly synchronized.

## Proposed Changes

### [Core Data Models]

#### [MODIFY] [StockData.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/StockData.kt)
- Implement `AssetData.updateMetadataFrom(other: AssetData)` to safely transfer `var` properties (`pros`, `cons`, `fieldSources`, `lastUpdated`, etc.) between instances.

### [ViewModel & Logic]

#### [MODIFY] [StockViewModel.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/StockViewModel.kt)
- **Refactor `saveManualAsset`**: Ensure it retrieves the existing asset from the database to preserve metadata before applying changes from the UI.
- **Update Timestamps**: Ensure `researchMarketScore` and `researchAllMarketScores` update the `lastUpdated` field so changes are recognized by the backup system.
- **Fix `recalculateAllScores`**: Add the missing database save operation so recalculated scores are persistent.

### [Persistence Layer]

#### [MODIFY] [ManualAssetDatabase.kt](file:///home/elias/AndroidStudioProjects/B3Check/app/src/main/java/com/example/b3check/ManualAssetDatabase.kt)
- **Refine `importBackup`**: Ensure it handles identical timestamps by prioritizing the imported data if it contains more information (or just always overwriting if it's a backup).

## Verification Plan

### Manual Verification
1.  **Metadata Preservation Test**:
    - Go to "Análise", add a "Pro" to an asset (e.g., PETR4).
    - Go to "Investir", change the "Cotas" (Shares) of PETR4.
    - Go back to "Análise" and verify the "Pro" is still present.
2.  **Export/Import Coherence**:
    - Research a Market Score for an asset.
    - Export the backup.
    - Delete the asset.
    - Import the backup.
    - Verify the asset is restored with the correct Score, Shares, and Price.
3.  **Recalculation Persistence**:
    - Run "Recalcular Notas" in the "Ativos" tab.
    - Restart the app.
    - Verify the notes remain updated.
