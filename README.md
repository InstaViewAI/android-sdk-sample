# InstaVision Guardian SDK — Android Sample

A working consumer camera app built on the InstaVision Guardian SDK (`ai.instavision:guardian`).
Sign up, sign in, browse your cameras, watch one live, and change its settings — the flows a real
product actually has, wired to the real SDK.

It is meant to be read as much as run. Every screen follows the same shape — a `ViewModel` that
calls the SDK and exposes one `StateFlow` of UI state — so you can open the screen closest to what
you are building and copy the pattern.

## What you need to supply

The sample will configure and compile without these, but it cannot talk to the platform until
you add them.

### 1. Firebase

Firebase Auth backs the SDK's account management.

1. Create a Firebase project in the [Firebase console](https://console.firebase.google.com/).
2. Register an Android app with the package name **`ai.instavision.sandbox`** — this must match
   the `applicationId` in `app/build.gradle.kts`, not the Kotlin `namespace`.
3. Download `google-services.json` and place it at `app/google-services.json`.

The `com.google.gms.google-services` plugin is applied **only when that file is present**, so the
project still compiles without it and you can verify the SDK resolves on your machine.

**Firebase is not optional at runtime.** `InstaVision.initialize` builds `UserServiceImpl`, whose
`init` block calls `Firebase.auth`, so initialising the SDK in a process without Firebase throws
`IllegalStateException: Default FirebaseApp is not initialized`. `SampleApp` therefore skips
`InstaVision.initialize` entirely when `google-services.json` is absent, and the catalog screen
shows what is missing instead of crashing. Every API call will fail until you add the file.

### 2. Partner credentials

Copy the credential keys from `local.properties.sample` into your own `local.properties`
(which is gitignored) and fill in the values issued to you by InstaVision:

```properties
instavision.partnerId=your-partner-id
instavision.clientId=your-client-id
```

They are surfaced to the app as `BuildConfig.PARTNER_ID` / `BuildConfig.CLIENT_ID`. If
`partnerId` is blank the app still starts and logs a warning, and the catalog screen shows a
banner telling you what is missing.

## How the SDK is integrated

Three changes to a stock Compose project — see the matching files for the real thing.

**`settings.gradle.kts`** — add the InstaVision Maven repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://nexus.instavision.ai/repository/maven-releases/")
    }
}
```

**`gradle/libs.versions.toml`** — declare the dependency:

```toml
[versions]
guardian = "2026.08.31"

[libraries]
guardian = { group = "ai.instavision", name = "guardian", version.ref = "guardian" }
```

**`app/build.gradle.kts`** — `implementation(libs.guardian)`.

### Toolchain requirements

The SDK is published as Java 19 bytecode built with Kotlin 2.4.10, so a consuming app needs:

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_19
    targetCompatibility = JavaVersion.VERSION_19
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_19) } }
```

with Kotlin **2.4.10 or newer**. An older Kotlin compiler cannot read the SDK's metadata and
fails with a "compiled with a newer Kotlin" error. `minSdk` must be 26 or higher.

## Initialising the SDK

`SampleApp` does this once, in `Application.onCreate`:

```kotlin
FirebaseApp.initializeApp(this)
InstaVision.initialize(
  applicationContext = this,
  config = InstaVisionConfig(
    partnerId = BuildConfig.PARTNER_ID,
    clientId = BuildConfig.CLIENT_ID,
    deviceId = deviceId,
    sessionId = sessionId,
  ),
)
```

`MainActivity` reports foreground changes so the SDK can release the microphone while the app
is backgrounded — without this, two-way audio goes silent:

```kotlin
override fun onStart() { super.onStart(); InstaVision.setAppInForeground(true) }
override fun onStop() { super.onStop(); InstaVision.setAppInForeground(false) }
```

## Architecture

Three pieces carry the whole app, and they are the parts worth copying.

**`ui/common/SdkResult.kt` — the callback bridge.** Every SDK method is callback-shaped
(`fun x(args…, onSuccess: (T) -> Unit, onError: (ApiError) -> Unit)`), which does not compose well
with coroutines or Compose state. One helper fixes that for the whole app:

```kotlin
suspend fun <T> sdkCall(
  block: (onSuccess: (T) -> Unit, onError: (ApiError) -> Unit) -> Unit,
): Result<T>
```

so a ViewModel reads as straight-line code:

```kotlin
sdkCall<List<Space>> { onSuccess, onError ->
  InstaVision.spaceServices.getSpaces(onSuccess = onSuccess, onError = onError)
}
  .onSuccess { spaces -> SessionStore.putSpaces(spaces) }
  .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
```

The type argument is usually needed explicitly — the SDK's lambda parameters give the compiler
nothing to infer from.

**`data/SessionStore.kt` — shared state.** Signed-in user, fetched spaces and cameras, and the
current selection. Screens read from it rather than re-fetching. Selecting a space clears the
cached cameras, because they belonged to the old one.

**One ViewModel per screen.** No composable calls `InstaVision` directly. Each exposes a single
`StateFlow<XxxUiState>` carrying `loading` / `error` / data, collected with
`collectAsStateWithLifecycle()`.

## The app

```
Onboarding ──► Sign Up ──► Verify Email ──┐
     └───────► Sign In ───────────────────┼──► HOME
                  └──► Forgot Password ───┘   (home switcher + camera list)
                                                    │
        ┌──────────────┬──────────────┬─────────────┼──────────────────┐
        ▼              ▼              ▼             ▼                  ▼
   Add a Home    Add a Camera   Camera Detail  Account Settings  Home & Members
   (create       (BLE pairing   (live view,    (profile,         (members,
    space)        wizard)        PTZ, mic,      password,         invites,
                                 siren)         region)           cellular)
                                      │
                                      ▼
                                Camera Settings
                                (name, detection, notifications,
                                 light, storage, firmware, lullaby)
```

| Screen | What it demonstrates |
|---|---|
| Onboarding | Entry point; no SDK calls |
| Sign Up | `signup`, local validation before touching the SDK |
| Verify Email | `sendVerificationEmail`, `getUser`, `User.emailVerified` |
| Sign In | `login`, `loginWithGoogle` — Google sign-in goes through Credential Manager |
| Forgot Password | `resetPassword` — note its callback order is `onError` **first** |
| Home | `getSpaces`, `getDevices`, home switching, `Device.isOnline()` |
| Add a Home | `createSpace` with a full `Address` |
| Add a Camera | BLE pairing — `bleService` + `createPairingSession` / `getPairingSessionStatus` |
| Camera Detail | `LiveStreamClient` — WebRTC video, two-way audio, recording, siren, PTZ |
| Camera Settings | Device info, motion, AI, notifications, light, SD card, firmware, lullaby |
| Account | `getUser`, `updateUser`, `updatePassword`, `deleteAccount`, `logout`, and `isPasswordAuthEnabled` to gate the change-password row |
| Home & Members | Space rename, features, members, invitations, cellular, `leaveSpace` |

### Live view — two things that will bite you

`players` emits `TextureViewRenderer` instances **the SDK already built and initialised**. Attach
the emitted instance and detach it from any previous parent, or you crash with "child already has
a parent":

```kotlin
AndroidView(
  factory = { FrameLayout(it) },
  update = { host ->
    host.removeAllViews()
    renderer?.let { view ->
      (view.parent as? ViewGroup)?.removeView(view)
      host.addView(view)
    }
  },
)
```

And `LiveStreamClient` **connects when you construct it** — `StreamingClient`'s init calls
`connect()` itself, guarded so it runs once. A closed client cannot be reopened, so retrying means
building a new client, not calling `connect()` again. Always `close()` when leaving the screen or
the connection and audio session leak.

### Camera pairing — the sequence that is not obvious

Pairing is the most intricate flow in the SDK, and the ordering is not what the docs imply. The
backend session must exist **first**, because its `sessionKey` is an input to the Wi-Fi handshake
sent over Bluetooth:

```
createPairingSession(spaceId, …)          -> sessionKey
  registerCallback(BleCallback)
  startScan()                             -> observe bleService.scanResults
  [user picks a camera]
  stopScan()                              -- required before connecting
  connectToDevice(bluetoothDevice)        -> onConnected()
  startServiceDiscovery()                 -> onDeviceReady()
  sendWifiScanCommand()                   -> observe bleService.wifiNetworks
  [user picks an SSID + password]
  sendWifiConfig(ssid, password, sessionKey, region, env)
                                          -> onWifiConfigSent()
  poll getPairingSessionStatus(spaceId, sessionKey) until status == "Processed"
  release()
```

Four things worth knowing, all found by reading the SDK rather than the docs:

- **`onWifiConfigSent()` does not mean the camera paired.** It means the credentials reached the
  camera. Whether it then reached the network and registered is only knowable by polling
  `getPairingSessionStatus`, whose terminal success value is `PairingStatus.PROCESSED.type`
  (`"Processed"`), not `"Success"`.
- **`onDeviceIdReceived` may never fire.** It arrives during service discovery, right before
  `onDeviceReady()`, and cameras without a device-id characteristic skip it entirely. Gate your
  step transitions on `onConnected()` and `onDeviceReady()`, never on the device id.
- **`release()` clears the registered callbacks**, so a restarted flow must re-register or it goes
  deaf.
- **`stopScan()` does not fire `onScanStopped()`** — only the 90-second scan timeout does.

`BleCallback` is an `open class` (declared inside `BleServiceImpl.kt`), not an interface, so you
subclass it. Its methods arrive on GATT binder threads — marshal to the main dispatcher before
touching Compose state.

### Calls that are expected to fail

Some features need hardware or account state a fresh test account does not have — live view
without a paired camera, lullaby controls on a model that has no speaker, cellular data on a
camera with no SIM. Those surface the SDK's `ApiError` message in the screen's error banner.
**That is the expected result, not a bug in the sample.**

This app covers the SDK surface a real product uses — roughly 65 of the SDK's 162 methods. The
event, SD-card playback, professional-monitoring, subscription and baby/pet/face services are not
exercised here.

## Building

```bash
./gradlew :app:assembleDebug
```

`./gradlew :app:compileDebugKotlin` compiles every call site without needing
`google-services.json`, which is a quick way to check the SDK resolves on your machine.
