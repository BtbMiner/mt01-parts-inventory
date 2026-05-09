package com.kemtdm.mt01.activity

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.kemtdm.mt01.utils.LanguageManager

abstract class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLocale(newBase))
    }
}