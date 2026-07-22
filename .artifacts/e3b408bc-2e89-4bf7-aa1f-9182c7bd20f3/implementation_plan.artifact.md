# Implementation Plan - AI Error Diagnostics and Stabilization

Improve error visibility for AI research and switch to a more stable Gemini model version to resolve the "Erro na IA (Cota)" issue.

## User Review Required

> [!IMPORTANT]
> **Detailed Errors**: I am replacing the generic "Erro na IA (Cota)" message with the actual error returned by Google. This will show you exactly if the problem is a "429 - Too Many Requests" (quota limit) or an invalid API key.

> [!NOTE]
> **Model Switch**: I am moving from `gemini-2.5-flash` to `gemini-1.5-flash`. The 1.5 version is the current industry standard for stability and high quota availability, which should reduce "Not Found" or "Internal Error" responses.

## Proposed Changes

### [AI Integration]

#### [MODIFY] [AndroidAiService.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/androidMain/kotlin/com/example/b3check/AndroidAiService.kt)
#### [MODIFY] [DesktopAiService.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/desktopMain/kotlin/com/example/b3check/DesktopAiService.kt)
- Change the API URL to use `gemini-1.5-flash`.

### [ViewModel Logic]

#### [MODIFY] [StockViewModel.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/StockViewModel.kt)
- Update `researchMarketScore` and `researchAllMarketScores` to display the raw error response in the status banner.

## Verification Plan

### Manual Verification
1.  **Test Research**: Go to "Análise" and tap the globe.
2.  **Verify Message**: If it still fails, the banner will now show a detailed message like "Erro: 429. Detalhes: ..." which we can use to troubleshoot further.
3.  **Check Success**: If the model switch fixes it, the market score will be successfully retrieved.
