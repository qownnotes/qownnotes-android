package org.qownnotes.mobile

import android.app.Activity
import android.content.Intent
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.model.SingleSignOnAccount

interface AccountImportGateway {
    fun begin(activity: Activity, onAccount: (SingleSignOnAccount) -> Unit)

    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onAccount: (SingleSignOnAccount) -> Unit
    )
}

class NextcloudAccountImportGateway : AccountImportGateway {
    override fun begin(activity: Activity, onAccount: (SingleSignOnAccount) -> Unit) {
        AccountImporter.pickNewAccount(activity)
    }

    override fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onAccount: (SingleSignOnAccount) -> Unit
    ) {
        AccountImporter.onActivityResult(requestCode, resultCode, data, activity, onAccount)
    }
}
