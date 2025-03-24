package com.thirfir.exoplayersample

import android.app.Dialog
import android.content.Context

class LoadingDialog(context: Context) : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

    init {
        setContentView(R.layout.dialog_loading)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }
}
