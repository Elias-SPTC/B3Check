# Walkthrough - Keyboard Overlap Fix

I have resolved the issue where the Android keyboard covers input fields when editing.

## Changes Made

### UI Layout Adjustment

#### [B3CheckUI.kt](file:///home/elias/AndroidStudioProjects/B3Check/shared/src/commonMain/kotlin/com/example/b3check/B3CheckUI.kt)

Added `Modifier.imePadding()` to the main `Scaffold`. This ensures that when `enableEdgeToEdge()` is active (as set in `MainActivity`), the UI automatically shrinks and shifts up to accommodate the keyboard, keeping the focused input field visible.

```diff
     Scaffold(
-        modifier = Modifier.fillMaxSize(),
+        modifier = Modifier.fillMaxSize().imePadding(),
         bottomBar = {
```

## Verification Results

### Manual Verification Recommendation

Since I cannot interact with a physical device, please perform the following check:
1. Open the "Investir" tab.
2. Tap the "Valor do aporte" text field at the bottom.
3. The entire screen (including the bottom navigation bar) should move up, keeping the input field above the keyboard.
