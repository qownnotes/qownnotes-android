package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.NextcloudDeck

/**
 * Opens Nextcloud Deck card links of the note's account in the Nextcloud Deck Android app.
 *
 * Deck does not handle web links, but it exports the activity Nextcloud Files uses to open a card
 * from a push notification. It finds the card by the SSO account name and the remote card ID, and
 * falls back to [EXTRA_LINK] in the browser when that account is not set up in Deck.
 */
internal class DeckCardOpener(private val context: Context) {
    /** `true` when the Deck app was started for [url], `false` to open it as an ordinary link. */
    fun open(url: String, account: Account?): Boolean {
        if (account == null || account.ssoAccountName.isBlank()) return false
        val card = NextcloudDeck.parseCardLink(url, account.serverUrl) ?: return false
        return DECK_PACKAGES.any { packageName ->
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(packageName, DECK_CARD_ACTIVITY))
                .putExtra(EXTRA_ACCOUNT, account.ssoAccountName)
                .putExtra(EXTRA_LINK, url)
                .putExtra(EXTRA_CARD_ID, card.cardId.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }

    internal companion object {
        /** F-Droid and Google Play builds; both must also be listed in the manifest queries. */
        val DECK_PACKAGES =
            listOf("it.niedermann.nextcloud.deck", "it.niedermann.nextcloud.deck.play")
        const val DECK_CARD_ACTIVITY = "it.niedermann.nextcloud.deck.ui.PushNotificationActivity"
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_LINK = "link"
        const val EXTRA_CARD_ID = "objectId"
    }
}
