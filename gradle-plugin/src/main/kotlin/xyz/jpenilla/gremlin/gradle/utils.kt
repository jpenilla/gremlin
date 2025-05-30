/*
 * gremlin
 *
 * Copyright (c) 2025 Jason Penilla
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

import org.gradle.api.file.FileSystemLocationProperty
import java.io.InputStream
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream

val FileSystemLocationProperty<*>.path: Path
    get() = get().asFile.toPath()

fun Path.jarUri(): URI = URI.create("jar:" + toUri().toString())

fun <R> Path.openZip(op: (FileSystem) -> R): R =
    FileSystems.newFileSystem(jarUri(), emptyMap<String, Any>()).use { fs ->
        op(fs)
    }

enum class HashingAlgorithm(val algorithmName: String) {
    SHA256("SHA-256"),
    SHA1("SHA-1");

    private val threadLocalMessageDigest = ThreadLocal.withInitial { createDigest() }

    fun createDigest(): MessageDigest = MessageDigest.getInstance(algorithmName)

    val threadLocalDigest: MessageDigest
        get() = threadLocalMessageDigest.get()
}

fun Path.hashFile(algorithm: HashingAlgorithm): ByteArray = inputStream().use { input -> input.hash(algorithm) }

fun InputStream.hash(algorithm: HashingAlgorithm, bufferSize: Int = 8192): ByteArray {
    val digest = algorithm.threadLocalDigest
    val buffer = ByteArray(bufferSize)
    while (true) {
        val count = read(buffer)
        if (count == -1) {
            break
        }
        digest.update(buffer, 0, count)
    }
    return digest.digest()
}

private val hexChars = "0123456789abcdef".toCharArray()

fun ByteArray.asHexString(): String {
    val chars = CharArray(2 * size)
    forEachIndexed { i, byte ->
        val unsigned = byte.toInt() and 0xFF
        chars[2 * i] = hexChars[unsigned / 16]
        chars[2 * i + 1] = hexChars[unsigned % 16]
    }
    return String(chars)
}
