package dev.sectile.network.internal.io.compression

import com.github.luben.zstd.Zstd
import dev.sectile.network.internal.NetworkInternalConnector
import dev.sectile.network.internal.io.ProtocolException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator

object PacketCompression {

    fun compress(src: ByteBuf, dst: ByteBuf, dstOffset: Int, maxDstSize: Int): Int {
        val srcSize = src.readableBytes()
        if (srcSize == 0) return 0

        val level = NetworkInternalConnector.config.zstdCompressionLevel

        if (src.isDirect && src.nioBufferCount() == 1 && dst.isDirect && dst.nioBufferCount() == 1) {
            val srcNio = src.internalNioBuffer(src.readerIndex(), srcSize)
            val dstNio = dst.nioBuffer(dstOffset, maxDstSize)

            val compressedSize = Zstd.compress(dstNio, srcNio, level)

            if (Zstd.isError(compressedSize.toLong())) {
                throw ProtocolException("Zstd compression failed: ${Zstd.getErrorName(compressedSize.toLong())}")
            }
            return compressedSize
        }

        // Fallback path for heap or composite buffers.
        val srcBytes = ByteArray(srcSize)
        src.getBytes(src.readerIndex(), srcBytes)
        val compressed = try {
            Zstd.compress(srcBytes, level)
        } catch (e: RuntimeException) {
            throw ProtocolException("Zstd compression failed: ${e.message}")
        }
        if (compressed.size > maxDstSize) {
            throw ProtocolException("Zstd output ${compressed.size} exceeds buffer $maxDstSize")
        }
        dst.setBytes(dstOffset, compressed)
        return compressed.size
    }

    fun decompress(
        alloc: ByteBufAllocator,
        src: ByteBuf,
        srcOffset: Int,
        srcLength: Int,
        maxDecompressedSize: Int,
    ): ByteBuf {
        if (src.isDirect && src.nioBufferCount() == 1) {
            val srcNio = src.internalNioBuffer(srcOffset, srcLength)

            val decompressedSize = Zstd.getFrameContentSize(srcNio)
            validateContentSize(decompressedSize, maxDecompressedSize)

            val sizeInt = decompressedSize.toInt()
            val dst = alloc.directBuffer(sizeInt)

            return try {
                val dstNio = dst.nioBuffer(0, sizeInt)
                val result = Zstd.decompress(dstNio, srcNio)

                if (Zstd.isError(result.toLong())) {
                    throw ProtocolException("Zstd decompression failed: ${Zstd.getErrorName(result.toLong())}")
                }
                if (result != sizeInt) {
                    throw ProtocolException("Zstd decompressed $result bytes, frame declared $sizeInt")
                }

                dst.writerIndex(result)
            } catch (e: Exception) {
                dst.release()
                throw e
            }
        }

        // Heap or composite buffer path.
        val srcBytes = ByteArray(srcLength)
        src.getBytes(srcOffset, srcBytes)

        val decompressedSize = Zstd.getFrameContentSize(srcBytes)
        validateContentSize(decompressedSize, maxDecompressedSize)

        val out = try {
            Zstd.decompress(srcBytes, decompressedSize.toInt())
        } catch (e: RuntimeException) {
            throw ProtocolException("Zstd decompression failed: ${e.message}")
        }
        return alloc.directBuffer(out.size).writeBytes(out)
    }

    private fun validateContentSize(size: Long, max: Int) {
        // -1 = unknown size, -2 = error; any negative value is invalid (including overflowing sizes).
        if (size < 0) throw ProtocolException("Zstd frame content size is unknown or invalid")
        if (size > max) throw ProtocolException("Decompressed size $size exceeds maximum $max")
    }
}
