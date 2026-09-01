package ai.instavision.sandbox.ui.auth

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

/** String resource the google-services plugin generates for the project's web OAuth client. */
private const val WEB_CLIENT_ID_RESOURCE = "default_web_client_id"

/** Bytes of randomness behind a request nonce; they are hashed before leaving the device. */
private const val NONCE_BYTES = 32

/** Digest the nonce is folded through, as Google's sign-in guidance prescribes. */
private const val NONCE_DIGEST = "SHA-256"

/** Reported when the sample has no `google-services.json`, so there is no client id to send. */
private const val NOT_CONFIGURED_MESSAGE =
  "Google sign-in needs your own google-services.json in the app module."

/** Reported when Credential Manager returns something that is not a Google ID token. */
private const val UNEXPECTED_CREDENTIAL_MESSAGE = "That credential is not a Google account."

/**
 * Reported when Play Services offers nothing back. It cannot tell an account-less phone from a
 * build whose signing certificate the Firebase project does not know, so the text names both — and
 * during development the certificate is much the likelier of the two.
 */
private const val NO_CREDENTIAL_MESSAGE =
  "No Google credential available. Check that a Google account is signed in on this device, and " +
    "that this build's signing certificate SHA-1 is registered in the Firebase project."

/**
 * Marks the one failure that is not worth a banner: the visitor dismissed the Credential Manager
 * sheet on purpose.
 */
class GoogleSignInCancelled(cause: Throwable? = null) :
  Exception("Google sign-in was dismissed", cause)

/** Reads the web OAuth client id the google-services plugin generates from `google-services.json`. */
object GoogleAuthConfig {
  /**
   * Null when the plugin never ran, which is this sample's state until a developer drops in their
   * own Firebase file — Google sign-in has to degrade rather than crash.
   */
  fun webClientId(context: Context): String? {
    val resourceId = context.resources.getIdentifier(
      WEB_CLIENT_ID_RESOURCE,
      "string",
      context.packageName,
    )
    return if (resourceId != 0) context.getString(resourceId) else null
  }
}

/**
 * Shows the Credential Manager account sheet and returns the Google ID token to hand to
 * `UserServices.loginWithGoogle`. [context] must be the Activity, because the sheet is drawn over
 * it; a deliberate dismissal comes back as a [GoogleSignInCancelled] failure so the caller can stay
 * quiet about it, while every other failure carries text worth showing.
 */
suspend fun requestGoogleIdToken(context: Context): Result<String> {
  val clientId = GoogleAuthConfig.webClientId(context)
    ?: return Result.failure(IllegalStateException(NOT_CONFIGURED_MESSAGE))
  val option = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(clientId)
    .setNonce(generateNonce())
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(option)
    .build()
  return try {
    val response = CredentialManager.create(context).getCredential(context, request)
    idTokenOf(response.credential)
  } catch (dismissed: GetCredentialCancellationException) {
    Result.failure(GoogleSignInCancelled(cause = dismissed))
  } catch (missing: NoCredentialException) {
    Result.failure(IllegalStateException(noCredentialMessage(missing), missing))
  } catch (failure: GetCredentialException) {
    Result.failure(failure)
  } catch (malformed: GoogleIdTokenParsingException) {
    Result.failure(malformed)
  }
}

/** Unwraps the Google ID token from a returned credential, refusing any other credential type. */
private fun idTokenOf(credential: Credential): Result<String> =
  if (
    credential is CustomCredential &&
    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
  ) {
    Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
  } else {
    Result.failure(IllegalStateException(UNEXPECTED_CREDENTIAL_MESSAGE))
  }

/** Adds whatever Play Services said for itself to [NO_CREDENTIAL_MESSAGE], when it said anything. */
private fun noCredentialMessage(failure: NoCredentialException): String =
  failure.message
    ?.takeIf { it.isNotBlank() }
    ?.let { "$NO_CREDENTIAL_MESSAGE ($it)" }
    ?: NO_CREDENTIAL_MESSAGE

/** Builds the hex-encoded, hashed nonce that ties a token to this one request. */
private fun generateNonce(): String {
  val bytes = ByteArray(NONCE_BYTES)
  SecureRandom().nextBytes(bytes)
  return MessageDigest.getInstance(NONCE_DIGEST)
    .digest(bytes)
    .joinToString(separator = "") { "%02x".format(it) }
}
