package com.example.engine

import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Skeletal Character Animation System for VOID REALMS V7.
 * Provides skeletal bone hierarchy, joint matrix transformations,
 * and pose keyframe interpolation for 3D humanoid characters.
 *
 * License: MIT License (Open Source 3D Skeletal Animation Architecture)
 */
object SkeletalAnimationEngine {

    enum class AnimState {
        IDLE, WALK, RUN, ATTACK_MELEE, SPELL_CAST, JUMP
    }

    data class BoneJoint(
        val name: String,
        val parentIndex: Int,
        var rotX: Float = 0f,
        var rotY: Float = 0f,
        var rotZ: Float = 0f,
        var transX: Float = 0f,
        var transY: Float = 0f,
        var transZ: Float = 0f
    )

    data class SkeletonPose(
        val bones: List<BoneJoint>
    )

    /**
     * Creates default humanoid 3D skeleton joints.
     */
    fun createHumanoidSkeleton(): SkeletonPose {
        return SkeletonPose(
            listOf(
                BoneJoint("Root", -1),                  // 0
                BoneJoint("Spine", 0, transY = 12f),     // 1
                BoneJoint("Head", 1, transY = 10f),      // 2
                BoneJoint("LeftShoulder", 1, transX = -8f), // 3
                BoneJoint("LeftElbow", 3, transY = -10f),  // 4
                BoneJoint("RightShoulder", 1, transX = 8f), // 5
                BoneJoint("RightElbow", 5, transY = -10f), // 6
                BoneJoint("LeftHip", 0, transX = -4f),     // 7
                BoneJoint("LeftKnee", 7, transY = -12f),   // 8
                BoneJoint("RightHip", 0, transX = 4f),     // 9
                BoneJoint("RightKnee", 9, transY = -12f)   // 10
            )
        )
    }

    /**
     * Computes animated joint rotations for skeletal poses based on animation state and time phase.
     */
    fun computePose(
        state: AnimState,
        animTimeSec: Float,
        attackProgress: Float = 0f
    ): SkeletonPose {
        val base = createHumanoidSkeleton()
        val bones = base.bones.map { it.copy() }.toMutableList()

        when (state) {
            AnimState.WALK, AnimState.RUN -> {
                val cycle = animTimeSec * if (state == AnimState.RUN) 12f else 7f
                val swing = sin(cycle) * 0.45f

                // Arm swing
                bones[3].rotX = swing
                bones[5].rotX = -swing

                // Leg swing
                bones[7].rotX = -swing * 1.2f
                bones[9].rotX = swing * 1.2f

                // Slight spine bob
                bones[1].transY = 12f + (cos(cycle * 2f) * 1.5f)
            }
            AnimState.ATTACK_MELEE -> {
                val swing = sin(attackProgress * Math.PI.toFloat()) * 1.4f
                bones[5].rotX = -swing
                bones[5].rotZ = -0.3f
                bones[1].rotY = swing * 0.4f
            }
            AnimState.SPELL_CAST -> {
                val pulse = sin(animTimeSec * 10f) * 0.2f
                bones[3].rotX = -1.2f + pulse
                bones[5].rotX = -1.2f + pulse
            }
            AnimState.JUMP -> {
                bones[7].rotX = -0.6f
                bones[9].rotX = -0.6f
                bones[8].rotX = 0.8f
                bones[10].rotX = 0.8f
            }
            AnimState.IDLE -> {
                val breath = sin(animTimeSec * 2.5f) * 0.08f
                bones[1].transY = 12f + breath * 10f
                bones[3].rotZ = breath
                bones[5].rotZ = -breath
            }
        }

        return SkeletonPose(bones)
    }
}
