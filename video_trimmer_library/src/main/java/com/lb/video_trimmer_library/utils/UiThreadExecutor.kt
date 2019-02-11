/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.lb.video_trimmer_library.utils

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import java.util.*

/**
 * This class provide operations for
 * UiThread tasks.
 */
internal object UiThreadExecutor {
    private val HANDLER = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val callback = msg.callback
            if (callback != null) {
                callback.run()
                decrementToken(msg.obj as Token)
            } else {
                super.handleMessage(msg)
            }
        }
    }

    private val TOKENS = HashMap<String, Token>()

    /**
     * Store a new task in the map for providing cancellation. This method is
     * used by AndroidAnnotations and not intended to be called by clients.
     *
     * @param id    the identifier of the task
     * @param task  the task itself
     * @param delay the delay or zero to run immediately
     */
    fun runTask(id: String, task: Runnable, delay: Long) {
        if ("" == id) {
            HANDLER.postDelayed(task, delay)
            return
        }
        val time = SystemClock.uptimeMillis() + delay
        HANDLER.postAtTime(task, nextToken(id), time)
    }

    private fun nextToken(id: String): Token {
        synchronized(TOKENS) {
            var token = TOKENS[id]
            if (token == null) {
                token = Token(id)
                TOKENS[id] = token
            }
            ++token.runnablesCount
            return token
        }
    }

    private fun decrementToken(token: Token) {
        synchronized(TOKENS) {
            if (--token.runnablesCount == 0) {
                val id = token.id
                val old = TOKENS.remove(id)
                if (old != token && old != null) {
                    // a runnable finished after cancelling, we just removed a
                    // wrong token, lets put it back
                    TOKENS[id] = old
                }
            }
        }
    }

    /**
     * Cancel all tasks having the specified `id`.
     *
     * @param id the cancellation identifier
     */
    fun cancelAll(id: String) {
        val token: Token?
        synchronized(TOKENS) {
            token = TOKENS.remove(id)
        }
        if (token == null) {
            // nothing to cancel
            return
        }
        HANDLER.removeCallbacksAndMessages(token)
    }

    // should not be instantiated
    private class Token constructor(internal val id: String) {
        internal var runnablesCount = 0
    }

}
