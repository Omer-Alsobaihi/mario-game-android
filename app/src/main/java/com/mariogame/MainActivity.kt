package com.mariogame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt


data class Player(
    var x: Float = 100f,
    var y: Float = 500f,
    var width: Float = 50f,
    var height: Float = 50f,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var isJumping = false,
    var onGround = false,
    var facingRight = true,
    var lives = 3,
    var score = 0
)

data class Platform(
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float
)

data class Coin(
    var x: Float,
    var y: Float,
    var radius: Float = 15f,
    var collected = false,
    var bounceOffset: Float = 0f
)

data class Enemy(
    var x: Float,
    var y: Float,
    var width: Float = 40f,
    var height: Float = 30f,
    var velocityX: Float = 2f,
    var alive = true
)

data class BackgroundStar(
    var x: Float,
    var y: Float,
    var size: Float,
    var speed: Float,
    var brightness: Float
)

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val paint = Paint()
    private var gameThread: GameThread? = null
    private var surfaceReady = false

    // Game objects
    private val player = Player()
    private val platforms = mutableListOf<Platform>()
    private val coins = mutableListOf<Coin>()
    private val enemies = mutableListOf<Enemy>()
    private val stars = mutableListOf<BackgroundStar>()

    // Game state
    private var score = 0
    private var lives = 3
    private var gameOver = false
    private var level = 1
    private var frameCount = 0
    private var lastJumpTime = 0f
    private val jumpCooldown = 300f // ms
    private val gravity = 0.8f
    private val moveSpeed = 5f
    private val jumpForce = -15f
    private var displayWidth = 0f
    private var displayHeight = 0f
    private var groundY = 0f
    private val scrollSpeed = 3f
    private val spawnTimer = 0
    private val enemySpawnInterval = 60 // frames

    // Touch controls
    private var touchX = 0f
    private var touchY = 0f
    private var isTouching = false
    private var wasTouching = false

    // Screen shake
    private var shakeIntensity = 0f
    private var shakeDuration = 0

    init {
        holder.addCallback(this)
        setFocusable(true)
        setFocusableInTouchMode(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (gameThread == null || !gameThread!!.isRunning) {
            gameThread = GameThread(holder)
            gameThread!!.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        displayWidth = width.toFloat()
        displayHeight = height.toFloat()
        groundY = displayHeight - 100f
        if (platforms.isEmpty()) {
            initializeLevel()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        gameThread?.stopThread()
        gameThread = null
    }

    private fun initializeLevel() {
        platforms.clear()
        coins.clear()
        enemies.clear()

        // Ground
        platforms.add(Platform(0f, groundY, displayWidth * 3, 100f))

        // Platforms generation based on level
        val platformSpacing = 200f
        val numPlatforms = 15 + level * 3
        var currentX = displayWidth + 100f

        for (i in 1..numPlatforms) {
            val pWidth = (100 + (i % 3) * 50).toFloat()
            val pY = (groundY - 100 - (i % 5) * 80).toFloat()
            platforms.add(Platform(currentX, pY, pWidth, 20f))

            // Add coins above platforms sometimes
            if (i % 2 == 0) {
                coins.add(Coin(currentX + pWidth / 2, pY - 30))
            }

            // Add enemy on some platforms
            if (i % 3 == 0 && i > 2) {
                enemies.add(Enemy(currentX + 20, pY - 30))
            }

            currentX += platformSpacing + (i % 4) * 30
        }

        // Background stars
        stars.clear()
        for (i in 1..50) {
            stars.add(BackgroundStar(
                (Math.random() * displayWidth).toFloat(),
                (Math.random() * groundY * 0.6).toFloat(),
                (2 + Math.random() * 3).toFloat(),
                (0.5f + Math.random() * 1.5f),
                (0.3f + Math.random() * 0.7f)
            ))
        }

        // Reset player
        player.x = 100f
        player.y = groundY - 50f
        player.velocityX = 0f
        player.velocityY = 0f
        player.onGround = true
        player.lives = 3
        player.score = 0
        score = 0
        lives = 3
        gameOver = false
        frameCount = 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchX = event.x.toFloat()
        touchY = event.y.toFloat()
        isTouching = event.action == MotionEvent.ACTION_DOWN ||
                     event.action == MotionEvent.ACTION_MOVE

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!gameOver) {
                    // Jump button area (bottom right)
                    if (touchX > displayWidth * 0.6 && touchY > displayHeight * 0.6) {
                        player.jump()
                    }
                } else {
                    // Restart
                    gameOver = false
                    initializeLevel()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Left/right movement based on touch position
                if (!gameOver) {
                    val moveZone = displayWidth * 0.4f
                    if (touchX < moveZone) {
                        player.moveLeft()
                    } else if (touchX > displayWidth - moveZone) {
                        player.moveRight()
                    } else {
                        player.stopMoving()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                player.stopMoving()
                isTouching = false
            }
        }
        return true
    }

    private fun update() {
        frameCount++

        if (gameOver) return

        // Apply gravity
        if (!player.onGround) {
            player.velocityY += gravity
        }

        // Update player position
        player.x += player.velocityX
        player.y += player.velocityY

        // Check ground collision
        player.onGround = false
        for (platform in platforms) {
            if (checkPlatformCollision(player, platform)) {
                player.onGround = true
                player.velocityY = 0f
                player.y = platform.y - player.height
            }
        }

        // Screen boundaries
        if (player.x < 0) player.x = 0
        if (player.x > displayWidth) player.x = displayWidth

        // Falling off screen
        if (player.y > displayHeight + 100) {
            player.lives--
            if (player.lives <= 0) {
                gameOver = true
            } else {
                player.x = 100f
                player.y = groundY - 50f
                player.velocityX = 0f
                player.velocityY = 0f
                shakeIntensity = 10f
                shakeDuration = 30
            }
        }

        // Scroll world
        scrollWorld()

        // Update platforms - remove off screen, add new
        updatePlatforms()

        // Update enemies
        updateEnemies()

        // Update coins
        updateCoins()

        // Update stars
        updateStars()

        // Decrease screen shake
        if (shakeDuration > 0) {
            shakeDuration--
            shakeIntensity *= 0.9f
        } else {
            shakeIntensity = 0f
        }

        // Check if player collected all coins in level
        if (coins.all { it.collected }) {
            level++
            initializeLevel()
        }
    }

    private fun scrollWorld() {
        // Scroll based on player position
        val scrollThreshold = displayWidth * 0.3f
        if (player.x > displayWidth - scrollThreshold) {
            val scrollAmount = player.velocityX
            if (scrollAmount > 0) {
                for (platform in platforms) {
                    platform.x -= scrollAmount
                }
                for (coin in coins) {
                    coin.x -= scrollAmount
                }
                for (enemy in enemies) {
                    enemy.x -= enemy.velocityX
                }
                for (star in stars) {
                    star.x -= scrollAmount * star.speed
                }
            }
        }

        // Player follows scroll
        if (player.x > displayWidth * 0.7f) {
            player.x = displayWidth * 0.7f
        }
    }

    private fun updatePlatforms() {
        // Remove platforms that are far left
        val toRemove = mutableListOf<Platform>()
        for (platform in platforms) {
            if (platform.x + platform.width < -200) {
                toRemove.add(platform)
            }
        }
        platforms.removeAll(toRemove)

        // Add new platforms when needed
        val lastPlatform = platforms.filter { it.y < groundY - 20 }.maxByOrNull { it.x }
        if (lastPlatform != null && lastPlatform.x < displayWidth + 300) {
            val newX = lastPlatform.x + 200 + (level * 30)
            val newY = (groundY - 100 - (level % 5) * 80).toFloat()
            platforms.add(Platform(newX, newY, 100 + (level % 3) * 30, 20f))

            // Add coin
            coins.add(Coin(newX + 50, newY - 30))

            // Add enemy occasionally
            if (level % 2 == 0) {
                enemies.add(Enemy(newX + 20, newY - 30))
            }
        }
    }

    private fun updateEnemies() {
        for (enemy in enemies) {
            if (!enemy.alive) continue

            enemy.x += enemy.velocityX

            // Bounce off walls
            if (enemy.x < 0 || enemy.x > displayWidth) {
                enemy.velocityX *= -1
            }

            // Check collision with player
            if (playerOnEnemy(player, enemy)) {
                if (!player.facingRight) {
                    // Stomp from left
                    enemy.alive = false
                    player.velocityY = -10f
                    player.score += 50
                } else {
                    // Hit from right
                    playerHit()
                }
            }
        }

        // Remove dead enemies after delay (visual)
        enemies.removeAll { !it.alive && it.x < -100 }
    }

    private fun updateCoins() {
        for (coin in coins) {
            if (coin.collected) continue

            // Bounce animation
            coin.bounceOffset = abs(sin(frameCount * 0.1f) * 5)

            // Check collection
            val dx = player.x + player.width / 2 - coin.x
            val dy = player.y + player.height / 2 - (coin.y + coin.bounceOffset)
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 40) {
                coin.collected = true
                player.score += 100
                score = player.score
            }
        }

        // Remove collected coins after animation
        coins.removeAll { it.collected && it.x < -50 }
    }

    private fun updateStars() {
        for (star in stars) {
            star.x -= scrollSpeed * star.speed
            if (star.x < 0) {
                star.x = displayWidth
                star.y = (Math.random() * groundY * 0.6).toFloat()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Apply screen shake
        if (shakeIntensity > 0) {
            canvas.translate(
                (Math.random() * shakeIntensity - shakeIntensity / 2).toFloat(),
                (Math.random() * shakeIntensity - shakeIntensity / 2).toFloat()
            )
        }

        // Clear background
        paint.color = Color.parseColor("#1a1a2e")
        canvas.drawRect(0f, 0f, displayWidth, displayHeight, paint)

        // Draw stars (background)
        for (star in stars) {
            val alpha = (star.brightness * 255 * (0.5f + 0.5f * sin(frameCount * 0.05f + star.x))).toInt()
            paint.color = Color.argb(alpha, 255, 255, 200)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(star.x, star.y, star.size, paint)
        }

        // Draw platforms
        paint.color = Color.parseColor("#e94560")
        paint.style = Paint.Style.FILL
        for (platform in platforms) {
            canvas.drawRect(platform.x, platform.y, platform.x + platform.width, platform.y + platform.height, paint)

            // Platform top highlight
            paint.color = Color.parseColor("#ff6b6b")
            canvas.drawRect(platform.x, platform.y, platform.x + platform.width, platform.y + 4, paint)
            paint.color = Color.parseColor("#e94560")
        }

        // Draw coins
        for (coin in coins) {
            if (coin.collected) continue
            val y = coin.y + coin.bounceOffset
            paint.color = Color.parseColor("#ffd700")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(coin.x, y, coin.radius, paint)

            // Coin shine
            paint.color = Color.parseColor("#fff8dc")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(coin.x - 3, y - 3, coin.radius / 3, paint)
        }

        // Draw enemies
        for (enemy in enemies) {
            if (!enemy.alive) continue
            paint.color = Color.parseColor("#6c5ce7")
            paint.style = Paint.Style.FILL

            // Enemy body
            canvas.drawRect(enemy.x, enemy.y, enemy.x + enemy.width, enemy.y + enemy.height, paint)

            // Enemy eyes
            paint.color = Color.WHITE
            canvas.drawRect(enemy.x + 5, enemy.y + 5, enemy.x + 12, enemy.y + 15, paint)
            canvas.drawRect(enemy.x + enemy.width - 12, enemy.y + 5, enemy.x + enemy.width - 5, enemy.y + 15, paint)

            // Enemy pupils
            paint.color = Color.BLACK
            canvas.drawRect(enemy.x + 7, enemy.y + 7, enemy.x + 10, enemy.y + 12, paint)
            canvas.drawRect(enemy.x + enemy.width - 10, enemy.y + 7, enemy.x + enemy.width - 7, enemy.y + 12, paint)

            // Enemy mouth
            paint.color = Color.parseColor("#2d3436")
            canvas.drawRect(enemy.x + 10, enemy.y + 18, enemy.x + 30, enemy.y + 22, paint)
        }

        // Draw player
        drawPlayer(canvas)

        // Draw UI
        drawUI(canvas)
    }

    private fun drawPlayer(canvas: Canvas) {
        val px = player.x
        val py = player.y

        // Body
        paint.color = Color.parseColor("#00b894")
        paint.style = Paint.Style.FILL
        canvas.drawRect(px, py, px + player.width, py + player.height, paint)

        // Overalls
        paint.color = Color.parseColor("#00cec9")
        canvas.drawRect(px + 5, py + 20, px + player.width - 5, py + 40, paint)

        // Head
        paint.color = Color.parseColor("#ffeaa7")
        canvas.drawRect(px + 5, py - 10, px + player.width - 5, py + 5, paint)

        // Hat
        paint.color = Color.parseColor("#e94560")
        canvas.drawRect(px, py - 18, px + player.width, py - 10, paint)
        canvas.drawRect(px + 10, py - 22, px + player.width - 10, py - 18, paint)

        // Eyes
        paint.color = Color.BLACK
        if (player.facingRight) {
            canvas.drawRect(px + 30, py - 5, px + 35, py, paint)
        } else {
            canvas.drawRect(px + 15, py - 5, px + 20, py, paint)
        }

        // Mustache
        paint.color = Color.parseColor("#2d3436")
        canvas.drawRect(px + 15, py + 2, px + 35, py + 4, paint)

        // Shoes
        paint.color = Color.parseColor("#6c5ce7")
        canvas.drawRect(px + 5, py + 40, px + 20, py + 50, paint)
        canvas.drawRect(px + player.width - 20, py + 40, px + player.width - 5, py + 50, paint)

        // Direction indicator
        if (!player.onGround) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(px + player.width / 2, py - 15, 8f, paint)
        }
    }

    private fun drawUI(canvas: Canvas) {
        paint.textSize = 40f
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        // Score
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Score: $score", 20f, 50f, paint)

        // Lives
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("❤️".repeat(lives), displayWidth - 20f, 50f, paint)

        // Level
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Level: $level", displayWidth / 2, 50f, paint)

        // Game Over
        if (gameOver) {
            paint.color = Color.parseColor("#e94560")
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 80f
            canvas.drawText("GAME OVER", displayWidth / 2, displayHeight / 2, paint)

            paint.color = Color.WHITE
            paint.textSize = 40f
            canvas.drawText("Tap to Restart", displayWidth / 2, displayHeight / 2 + 60, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameThread?.stopThread()
        gameThread = null
    }

    private fun checkPlatformCollision(player: Player, platform: Platform): Boolean {
        // Simple AABB collision
        return player.x < platform.x + platform.width &&
                player.x + player.width > platform.x &&
                player.y < platform.y + platform.height &&
                player.y + player.height > platform.y
    }

    private fun playerOnEnemy(player: Player, enemy: Enemy): Boolean {
        return player.x < enemy.x + enemy.width &&
                player.x + player.width > enemy.x &&
                player.y < enemy.y + enemy.height &&
                player.y + player.height > enemy.y
    }

    private fun playerHit() {
        shakeIntensity = 15f
        shakeDuration = 50
        player.lives--
        if (player.lives <= 0) {
            gameOver = true
        } else {
            player.x = 100f
            player.y = groundY - 50f
            player.velocityX = 0f
            player.velocityY = 0f
        }
    }

    inner class GameThread(private val surfaceHolder: SurfaceHolder) : Thread() {
        private var running = false

        fun startGame() {
            running = true
            start()
        }

        fun stopThread() {
            running = false
            try {
                join(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            while (running) {
                val canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    try {
                        if (surfaceReady) {
                            update()
                            onDraw(canvas)
                        }
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
                // Frame rate control (~60fps)
                try {
                    Thread.sleep(16)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun resume() {
        super.resume()
        if (gameThread == null || !gameThread!!.isAlive) {
            gameThread = GameThread(holder)
            gameThread!!.start()
        }
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var gameSurfaceView: GameSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameSurfaceView = GameSurfaceView(this)
        setContentView(gameSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        gameSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        gameSurfaceView.onPause()
    }
}
