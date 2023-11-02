/*
 * gremlin
 *
 * Copyright (c) 2023 Jason Penilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.jpenilla.gremlin.gradle

import java.io.InputStream
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.io.path.inputStream

enum class HashingAlgorithm(algorithm: String) {
    SHA256("SHA-256"),
    SHA1("SHA-1");

    private val threadLocalMessageDigest = ThreadLocal.withInitial { MessageDigest.getInstance(algorithm) }

    val digest: MessageDigest
        get() = threadLocalMessageDigest.get()
}

fun Path.hashFile(algorithm: HashingAlgorithm): ByteArray = inputStream().use { input -> input.hash(algorithm) }

fun InputStream.hash(algorithm: HashingAlgorithm): ByteArray {
    val digestStream = DigestInputStream(this, algorithm.digest)
    digestStream.use { stream ->
        val buffer = ByteArray(1024)
        while (stream.read(buffer) != -1) {
            // reading
        }
    }
    return digestStream.messageDigest.digest()
}

fun ByteArray.asHexString(): String {
    val sb: StringBuilder = StringBuilder(size * 2)
    for (aHash in this) {
        sb.append("%02x".format(aHash.toInt() and 0xFF))
    }
    return sb.toString()
}
