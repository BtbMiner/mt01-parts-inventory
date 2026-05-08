package com.kemtdm.mt01.utils

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*

class QuantitySelectorHandler(
    private val etQty: EditText,
    private val btnMinus: MaterialButton,
    private val btnPlus: MaterialButton,
    private val scope: CoroutineScope
) {
    private var repeatJob: Job? = null
    private val repeatInterval = 100L // 100ms
    private val initialDelay = 500L  // 500ms before auto-repeat starts

    init {
        setupListeners()
    }

    private fun setupListeners() {
        btnMinus.setOnClickListener { increment(-1.0) }
        btnPlus.setOnClickListener { increment(1.0) }

        setupLongPress(btnMinus, -1.0)
        setupLongPress(btnPlus, 1.0)
    }

    fun setupShortcuts(btn1: View, btn5: View, btn10: View) {
        btn1.setOnClickListener { increment(1.0) }
        btn5.setOnClickListener { increment(5.0) }
        btn10.setOnClickListener { increment(10.0) }
    }

    private fun increment(delta: Double) {
        val current = etQty.text.toString().toDoubleOrNull() ?: 0.0
        val newValue = (current + delta).coerceAtLeast(0.0)
        
        // Format to remove .0 if it's an integer
        val text = if (newValue % 1.0 == 0.0) newValue.toInt().toString() else newValue.toString()
        etQty.setText(text)
        etQty.setSelection(etQty.text.length)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLongPress(view: View, delta: Double) {
        view.setOnLongClickListener {
            startRepeating(delta)
            true
        }

        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRepeating()
            }
            false
        }
    }

    private fun startRepeating(delta: Double) {
        repeatJob?.cancel()
        repeatJob = scope.launch {
            while (isActive) {
                increment(delta)
                delay(repeatInterval)
            }
        }
    }

    private fun stopRepeating() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun getValue(): Double = etQty.text.toString().toDoubleOrNull() ?: 0.0
    
    fun setValue(value: Double) {
        val text = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        etQty.setText(text)
    }
}