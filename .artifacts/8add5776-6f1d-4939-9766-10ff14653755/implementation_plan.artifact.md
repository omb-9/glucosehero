# Fix Startup Crash: Adaptive Icon in Compose

The app crashes on startup because `LogScreen` (the start destination) tries to render `R.mipmap.ghlogo` using Compose's `painterResource`. In `res/mipmap-anydpi-v26/`, `ghlogo.xml` is defined as an `<adaptive-icon>`, which `painterResource` does not support.

## Proposed Changes

### UI Resources

#### [NEW] [ic_logo_display.xml](file:///C:/Users/obond/Desktop/Projects/GlucoseHero/App/GlucoseHero/app/src/main/res/drawable/ic_logo_display.xml)
Create a new vector drawable specifically for UI display by extracting the foreground path from the adaptive icon.

### UI Components

#### [MODIFY] [LogScreen.kt](file:///C:/Users/obond/Desktop/Projects/GlucoseHero/App/GlucoseHero/app/src/main/java/com/omb9/glucosehero/ui/log/LogScreen.kt)
Update the empty state `Image` to use `R.drawable.ic_logo_display`.

#### [MODIFY] [ChatScreen.kt](file:///C:/Users/obond/Desktop/Projects/GlucoseHero/App/GlucoseHero/app/src/main/java/com/omb9/glucosehero/ui/chat/ChatScreen.kt)
Update the empty state `Image` to use `R.drawable.ic_logo_display`.

## Verification Plan

### Automated Tests
- Run `gradlew assembleDebug` to ensure resource IDs are correctly generated and the app builds.

### Manual Verification
1. Deploy the app to a device or emulator.
2. Observe that the app no longer crashes on startup.
3. Verify that the `Log` screen shows the logo in its empty state.
4. Navigate to the `Hero` tab and verify the logo appears there when no messages are present.
