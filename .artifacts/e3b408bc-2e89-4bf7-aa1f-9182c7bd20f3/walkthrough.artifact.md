# Walkthrough - AI Research Status in Analysis Tab

I have implemented visual feedback and progress tracking in the "Análise" tab to keep you informed during market score research.

## Changes Made

### Visual Feedback

#### [B3CheckUI.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/B3CheckUI.kt)

I added a status banner in `StockAnalysisScreen` that reacts to the background research process triggered by the **Globe** icon.

- **Progress Bar**: When you start a research, a `LinearProgressIndicator` appears along with the "Pesquisando..." text, showing that the app is actively working.
- **Error & Warning Highlighting**:
    - **Missing Key**: Shows an orange warning if the API key is not configured.
    - **Errors**: Displays a red banner with an error icon if the research fails (e.g., network issue or invalid response).
- **Auto-Hide**: The banner automatically disappears once the status returns to "Pronto", keeping the interface clean.

## Verification Results

### Manual Verification
1. **Globe Icon Action**: Navigate to "Análise", search for an asset, and tap the globe. Verify the status banner appears with a loading bar.
2. **Success Case**: After a few seconds, the banner should vanish and the "Mercado" score should be updated.
3. **Missing Key**: Remove the API key in the "IA Global" settings, then try the globe again in "Análise". Verify the orange warning correctly guides you to the settings.
4. **Consistency**: Confirm that the colors and icons match the ones used in the "IA Global" tab for a unified look.
