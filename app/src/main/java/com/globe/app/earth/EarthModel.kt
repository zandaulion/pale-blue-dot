package com.globe.app.earth

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates a UV sphere mesh suitable for rendering the Earth.
 *
 * The mesh contains interleaved vertex data: position (3), normal (3), texCoord (2)
 * packed as 8 floats per vertex. Indices use GL_TRIANGLES.
 *
 * @param latSegments  Number of horizontal rings (latitude divisions).
 * @param lonSegments  Number of vertical slices (longitude divisions).
 */
class EarthModel(
    private val latSegments: Int = 64,
    private val lonSegments: Int = 128
) {
    /** Stride in floats per vertex: px, py, pz, nx, ny, nz, u, v */
    val floatsPerVertex = 8

    /** Stride in bytes */
    val strideBytes = floatsPerVertex * Float.SIZE_BYTES

    val vertexBuffer: FloatBuffer
    val indexBuffer: IntBuffer
    val indexCount: Int

    init {
        val vertices = generateVertices()
        val indices = generateIndices()
        indexCount = indices.size

        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                flip()
            }

        indexBuffer = ByteBuffer
            .allocateDirect(indices.size * Int.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply {
                put(indices)
                flip()
            }
    }

    // -----------------------------------------------------------------------
    // Mesh generation
    // -----------------------------------------------------------------------

    private fun generateVertices(): FloatArray {
        val vertexCount = (latSegments + 1) * (lonSegments + 1)
        val data = FloatArray(vertexCount * floatsPerVertex)
        var offset = 0

        for (lat in 0..latSegments) {
            // theta goes from 0 (north pole) to PI (south pole)
            val theta = Math.PI * lat / latSegments
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()

            for (lon in 0..lonSegments) {
                // phi goes from 0 to 2*PI
                val phi = 2.0 * Math.PI * lon / lonSegments
                val sinPhi = sin(phi).toFloat()
                val cosPhi = cos(phi).toFloat()

                // Position on unit sphere
                val x = sinTheta * cosPhi
                val y = cosTheta
                val z = sinTheta * sinPhi

                // Normal is the same as position for a unit sphere
                val nx = x
                val ny = y
                val nz = z

                // Texture coordinates
                val u = lon.toFloat() / lonSegments
                val v = lat.toFloat() / latSegments

                data[offset++] = x
                data[offset++] = y
                data[offset++] = z
                data[offset++] = nx
                data[offset++] = ny
                data[offset++] = nz
                data[offset++] = u
                data[offset++] = v
            }
        }
        return data
    }

    private fun generateIndices(): IntArray {
        val indices = mutableListOf<Int>()
        val vertsPerRow = lonSegments + 1

        for (lat in 0 until latSegments) {
            for (lon in 0 until lonSegments) {
                val topLeft = lat * vertsPerRow + lon
                val topRight = topLeft + 1
                val bottomLeft = topLeft + vertsPerRow
                val bottomRight = bottomLeft + 1

                // Two triangles per quad (CCW winding)
                indices.add(topLeft)
                indices.add(bottomLeft)
                indices.add(topRight)

                indices.add(topRight)
                indices.add(bottomLeft)
                indices.add(bottomRight)
            }
        }
        return indices.toIntArray()
    }

    // -----------------------------------------------------------------------
    // GPU buffer helpers
    // -----------------------------------------------------------------------

    /**
     * Uploads the mesh to the GPU and returns a [GpuBuffers] handle.
     * The caller is responsible for calling [GpuBuffers.release] when done.
     */
    fun uploadToGpu(): GpuBuffers {
        val ids = IntArray(3) // VAO, VBO, EBO
        GLES30.glGenVertexArrays(1, ids, 0)
        GLES30.glGenBuffers(2, ids, 1)

        val vao = ids[0]
        val vbo = ids[1]
        val ebo = ids[2]

        GLES30.glBindVertexArray(vao)

        // Upload vertex data
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexBuffer.capacity() * Float.SIZE_BYTES,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Position attribute (location = 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0, 3, GLES30.GL_FLOAT, false, strideBytes, 0
        )

        // Normal attribute (location = 1)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1, 3, GLES30.GL_FLOAT, false, strideBytes, 3 * Float.SIZE_BYTES
        )

        // Texture coordinate attribute (location = 2)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(
            2, 2, GLES30.GL_FLOAT, false, strideBytes, 6 * Float.SIZE_BYTES
        )

        // Upload index data
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexBuffer.capacity() * Int.SIZE_BYTES,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Unbind VAO (leave EBO bound inside the VAO state)
        GLES30.glBindVertexArray(0)

        return GpuBuffers(vao, vbo, ebo, indexCount)
    }

    /**
     * Holds GPU resource handles for the uploaded sphere mesh.
     */
    data class GpuBuffers(
        val vao: Int,
        val vbo: Int,
        val ebo: Int,
        val indexCount: Int
    ) {
        fun release() {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            GLES30.glDeleteBuffers(2, intArrayOf(vbo, ebo), 0)
        }
    }
}
