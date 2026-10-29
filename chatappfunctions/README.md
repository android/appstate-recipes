# Chat AppFunctions (full app sample)

Unlike the single-concept recipes in [`app`](../app), this sample is a complete chat app for phone
and Wear OS. It exposes its features to on-device AI agents through
[AppFunctions](https://developer.android.com/ai/appfunctions) and uses **`StateStore`** to share
application-level state between the AppFunctions service and the UI.

It shows how an agent can change what the app is doing, not only call into it:
- **`StateStore`** holds the application-level state (the signed-in user and the screen being
  shown) in one Hilt singleton, shared by the AppFunctions service and the activities.
- **AppFunctions** such as `changeUser` and `changeScreen` each change one piece of that state, so
  an agent can switch users or open a conversation, and the open app reacts right away.

---

## Modules

| Module | Contents |
|---|---|
| [`shared`](shared) | AppFunctions service, `ChatAppStateHolder`, repositories, and ViewModels used by both apps |
| [`app`](app) | Phone app (Compose, Navigation) |
| [`wear`](wear) | Wear OS app (Wear Compose, Navigation 3) |

---

## StateStore for application-level state

### A state holder with private keys
[`ChatAppStateHolder`](shared/src/main/kotlin/com/example/chatapp/data/ChatAppStateHolder.kt)
wraps the single `StateStore` and declares a `private` `StateStoreKey` for each piece of state.
Callers read `State` properties and call setters on the holder, so the keys can't be used from
anywhere else:

```kotlin
private object CurrentUserNameKey : StateStoreKey<String>(DEFAULT_USER_NAME)

private object CurrentScreenKey : StateStoreKey<AppScreen>(AppScreen.ChatList)

@Singleton
class ChatAppStateHolder @Inject constructor(private val stateStore: StateStore) {
    val currentUserName: State<String> = stateStore.getState(CurrentUserNameKey)
    val currentScreen: State<AppScreen> = stateStore.getState(CurrentScreenKey)

    fun setCurrentUserName(name: String) = stateStore.setState(CurrentUserNameKey, name)
    fun setCurrentScreen(screen: AppScreen) = stateStore.setState(CurrentScreenKey, screen)
}

@Module
@InstallIn(SingletonComponent::class)
object StateStoreModule {
    @Provides
    @Singleton
    fun provideStateStore(): StateStore = StateStore()
}
```

The AppFunctions service and the activities run in the same process, so they receive the same
`ChatAppStateHolder` (and `StateStore`) and see each other's changes immediately, whether or not
the activity is open when an agent calls a function.

### Who reads and writes it

| Component | How it uses `ChatAppStateHolder` |
|---|---|
| [`BaseChatAppFunctionService`](shared/src/main/kotlin/com/example/chatapp/appfunctions/BaseChatAppFunctionService.kt) | `changeUser` and `changeScreen` write the user name and the current screen. |
| Phone [`MainActivity`](app/src/main/kotlin/com/example/chatapp/MainActivity.kt) and Wear [`MainActivity`](wear/src/main/kotlin/com/example/chatapp/wear/MainActivity.kt) | Navigate when the current screen is changed outside the UI, and record the screen when the user navigates. |
| [`RecipientsViewModel`](shared/src/main/kotlin/com/example/chatapp/RecipientsViewModel.kt) | Shows "Signed in as …" on the chat list. |
| [`ChatViewModel`](shared/src/main/kotlin/com/example/chatapp/ChatViewModel.kt) and [`MessageRepository`](shared/src/main/kotlin/com/example/chatapp/data/MessageRepository.kt) | Use the current user name as the sender of outgoing messages. |

### Keeping navigation in sync
Each `MainActivity` runs two effects. One shows the screen from `ChatAppStateHolder` when it differs
from the screen on display, for example after an agent calls `changeScreen`. The other records the
destination the user navigates to. All writes happen in `LaunchedEffect`, not during composition:

```kotlin
val currentScreen = appState.currentScreen.value
LaunchedEffect(currentScreen) {
    val shownScreen = navController.currentBackStackEntry?.toAppScreen()
    if (shownScreen != null && shownScreen != currentScreen) {
        navController.show(currentScreen)
    }
}
val currentEntry by navController.currentBackStackEntryAsState()
LaunchedEffect(currentEntry) {
    // Read the live entry: the effect above may have just navigated.
    navController.currentBackStackEntry?.toAppScreen()?.let(appState::setCurrentScreen)
}
```

`show` pops back to the chat list before navigating to another screen, so repeated `changeScreen`
calls from an agent replace the screen on top of the chat list instead of stacking destinations on
the back stack.

---

## AppFunctions

All functions are declared in
[`BaseChatAppFunctionService.kt`](shared/src/main/kotlin/com/example/chatapp/appfunctions/BaseChatAppFunctionService.kt)
with `@AppFunctionDeclaration` and described with KDoc, which agents read to decide when and how to
call them:

| Function | What it does |
|---|---|
| `searchContacts(query, contactType)` | Finds contacts or groups (`"INDIVIDUAL"`, `"GROUP"`, or `"ANY"`) and returns their `endpointValue`s. |
| `fetchSendMessageDetails(endpointValue, messageBody, imageUris)` | Returns a confirmation preview before sending a message. |
| `sendMessage(endpointValue, messageBody, imageUris)` | Sends a message to a contact or group. |
| `makeCall(endpointValue)` | Starts a call and returns a `PendingIntent` that opens the call screen. |
| `updateChatWallpaper(chatId, wallpaperUri)` | Changes a chat's wallpaper. |
| `searchMessages(query, endpointValue)` | Searches messages, optionally in one conversation. |
| `changeUser(userName)` | Changes the signed-in user. |
| `changeScreen(screen, endpointValue)` | Shows `"CHAT_LIST"`, `"CHAT"` (requires `endpointValue`), or `"SETTINGS"`. |

---

## Running the sample

The AppFunctions platform APIs require a device or emulator running **Android 16 or later**.

The apps use the application IDs `com.example.appstaterecipes.chatapp` and
`com.example.appstaterecipes.chatapp.wear`, so they can be installed next to the original ChatApp
from [android/appfunctions](https://github.com/android/appfunctions).

Select the **chatappfunctions.app** (phone) or **chatappfunctions.wear** run configuration in
Android Studio, or run:

```
./gradlew :chatappfunctions:app:installDebug
```

### Calling the functions like an agent
`adb shell cmd app_function` calls AppFunctions through the same system service that on-device
agents use. With the app open, try:

```bash
# Change the signed-in user; the chat list shows "Signed in as Jordan"
adb shell "cmd app_function execute-app-function \
  --package com.example.appstaterecipes.chatapp \
  --function 'com.example.chatapp.appfunctions.BaseChatAppFunctionService#changeUser' \
  --parameters '{\"userName\": \"Jordan\"}'"

# Open the chat with Bob Johnson
adb shell "cmd app_function execute-app-function \
  --package com.example.appstaterecipes.chatapp \
  --function 'com.example.chatapp.appfunctions.BaseChatAppFunctionService#changeScreen' \
  --parameters '{\"screen\": \"CHAT\", \"endpointValue\": \"2\"}'"

# Find a contact's endpointValue
adb shell "cmd app_function execute-app-function \
  --package com.example.appstaterecipes.chatapp \
  --function 'com.example.chatapp.appfunctions.BaseChatAppFunctionService#searchContacts' \
  --parameters '{\"query\": \"Alice\", \"contactType\": \"INDIVIDUAL\"}'"
```

To list the functions and their descriptions:

```bash
adb shell cmd app_function list-app-functions --package com.example.appstaterecipes.chatapp
```

## Tests

```
./gradlew :chatappfunctions:app:connectedDebugAndroidTest
```

- [`AppFunctionInstrumentationTest`](app/src/androidTest/kotlin/com/example/chatapp/appfunctions/AppFunctionInstrumentationTest.kt)
  executes them through `AppFunctionManager`, the API agents use, and checks that the change shows
  up in the app's shared `ChatAppStateHolder`.

## Libraries used
- AppState: `statestore`
- AppFunctions (`1.0.0-beta01`): `appfunctions`, `appfunctions-compiler`
- Hilt, Navigation (phone), Navigation 3 and Wear Compose (Wear), Coil
