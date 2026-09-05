package org.qownnotes.mobile.markdown

import java.io.InputStream

/**
 * Abstraction for making SSO-authenticated HTTP requests to fetch Nextcloud note attachments.
 *
 * The `markdown-android` module cannot depend on the SSO library, so the `app` module provides
 * an implementation that delegates to the Nextcloud SSO API.
 */
fun interface NextcloudAttachmentHttpClient {
    /**
     * Fetches the resource at [url] using SSO authentication for the given account.
     *
     * @param url the full URL of the attachment endpoint.
     * @param accountName the SSO account name to authenticate with.
     * @return the response body as an [InputStream], or `null` on failure.
     */
    fun fetch(url: String, accountName: String): InputStream?
}
