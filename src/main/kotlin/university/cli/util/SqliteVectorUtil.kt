package university.cli.util

import university.cli.model.Vector
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SqliteVectorUtil {
    private const val FLOAT_BYTES = Float.SIZE_BYTES

    fun toFloat32Blob(vector: Vector): ByteArray {
        val buffer = ByteBuffer.allocate(vector.values.size * FLOAT_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        vector.values.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun fromFloat32Blob(bytes: ByteArray): Vector {
        require(bytes.isNotEmpty() && bytes.size % FLOAT_BYTES == 0) {
            "Invalid sqlite-vec float32 BLOB size: ${bytes.size}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return Vector(List(bytes.size / FLOAT_BYTES) { buffer.float })
    }
}
