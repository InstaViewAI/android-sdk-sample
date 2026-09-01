package ai.instavision.sandbox

import ai.instavision.guardian.sdk.InstaVision
import ai.instavision.guardian.sdk.data.entity.InstaVisionConfig
import ai.instavision.network.data.enums.Environment
import ai.instavision.network.data.enums.ServerRegions
import ai.instavision.sandbox.data.notifications.NotificationChannels
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import java.util.UUID

/** Log tag for the sample's own start-up diagnostics. */
private const val TAG = "SampleApp"

/** Preferences file holding the installation-scoped device id the SDK is configured with. */
private const val PREFS_NAME = "instavision_sample"

/** Key under which the generated device id is persisted. */
private const val KEY_DEVICE_ID = "device_id"

/**
 * Boots Firebase and the Guardian SDK once per process, before any screen can call a service.
 * The two are inseparable: the SDK's user service reaches for `Firebase.auth` from its own `init`
 * block, so initialising it in a process without Firebase crashes on a background coroutine.
 */
class SampleApp : Application() {
  override fun onCreate() {
    super.onCreate()
    setupProblem?.let { Log.w(TAG, "$it Every SDK call will fail.") }
    installationId = resolveInstallationId()
    NotificationChannels.create(this)
    if (!BuildConfig.HAS_FIREBASE_CONFIG) return
    FirebaseApp.initializeApp(this)
    InstaVision.initialize(
      applicationContext = applicationContext,
      config = InstaVisionConfig(
        partnerId = BuildConfig.PARTNER_ID,
        clientId = BuildConfig.CLIENT_ID,
        deviceId = installationId,
        sessionId = sessionId,
        region = region,
        environment = environment,
      ),
    )
    sdkInitialized = true
  }

  /** Returns the installation-scoped device id, generating and persisting it on first launch. */
  private fun resolveInstallationId(): String {
    val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
      prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }
  }

  companion object {
    /** Backing flag for [isSdkInitialized]; only ever written from [onCreate]. */
    private var sdkInitialized: Boolean = false

    /** Identifies this process to the backend; deliberately not persisted across launches. */
    val sessionId: String = UUID.randomUUID().toString()

    /**
     * Installation-scoped id the SDK is configured with as its `deviceId` and push registration
     * reports as `app_id`. Empty only before [onCreate] has run, which no caller can observe.
     *
     * Named for the installation rather than the device because `Context.getDeviceId()` already
     * means something else, and shadowing it inside an `Application` does not compile.
     */
    var installationId: String = ""
      private set

    /** Region the next [InstaVision.initialize] uses; a settings screen may rewrite it. */
    var region: ServerRegions = ServerRegions.US

    /** Environment the next [InstaVision.initialize] uses; a settings screen may rewrite it. */
    var environment: Environment = Environment.RELEASE

    /** True once [InstaVision.initialize] has actually been called for this process. */
    val isSdkInitialized: Boolean get() = sdkInitialized

    /** The first missing piece of build configuration, or null when the sample is ready to run. */
    val setupProblem: String? get() = when {
      !BuildConfig.HAS_FIREBASE_CONFIG ->
        "Missing app/google-services.json - the SDK cannot start without Firebase."
      BuildConfig.PARTNER_ID.isBlank() -> "Missing instavision.partnerId in local.properties."
      BuildConfig.CLIENT_ID.isBlank() -> "Missing instavision.clientId in local.properties."
      else -> null
    }
  }
}
