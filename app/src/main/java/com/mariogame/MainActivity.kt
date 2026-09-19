package com.mariogame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint()
    private var gameThread: Thread? = null
    private var running = false
    
    private var playerX = 100f
    private var playerY = 500f
    private val playerW = 50f
    private val playerH = 50f
    private var velocityY = 0f
    private var onGround = true
    
    private val gravity = 0.6f
    private val jumpForce = -12f
    
    private var score = 0
    private var gameOver = false
    
    init {
        holder.addCallback(this)
        setFocusable(true)
        setFocusableInTouchMode(true)
    }
    
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!running) {
            running = true
            gameThread = Thread {
                while (running) {
                    drawFrame()
                    try { Thread.sleep(16) } catch (e: InterruptedException) {}
                }
            }
            gameThread?.start()
        }
    }
    
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        gameThread?.join(1000)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!gameOver) {
                    if (onGround) {
                        velocityY = jumpForce
                        onGround = false
                    }
                } else {
                    gameOver = false
                    playerX = 100f
                    playerY = 500f
                    velocityY = 0f
                    score = 0
                }
            }
        }
        return true
    }
    
    private fun drawFrame() {
        val canvas = h...[truncated]
