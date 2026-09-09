package org.example.timeloop.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerKinematicsTest {

    @Test
    void storesTickEndWorldCenterState() {
        PlayerKinematics kinematics = kinematics(
                MovementState.CRUISING,
                ActorPhase.PHASED,
                12,
                false,
                AnimationState.MOVING);

        assertEquals(3L, kinematics.tick());
        assertEquals(96.0, kinematics.x());
        assertEquals(144.0, kinematics.y());
        assertEquals(Direction.RIGHT, kinematics.direction());
        assertEquals(ActorPhase.PHASED, kinematics.actorPhase());
        assertEquals(12, kinematics.actorPhaseTicksRemaining());
    }

    @Test
    void animationStateIsDerivedFromInteractionAndMovementState() {
        assertEquals(AnimationState.INTERACTING, kinematics(
                MovementState.DOCKED,
                ActorPhase.AVAILABLE,
                0,
                true,
                AnimationState.INTERACTING).animationState());
        assertEquals(AnimationState.DOCKED, kinematics(
                MovementState.DOCKED,
                ActorPhase.AVAILABLE,
                0,
                false,
                AnimationState.DOCKED).animationState());
        assertEquals(AnimationState.MOVING, kinematics(
                MovementState.SLOWED,
                ActorPhase.RECOVERING,
                4,
                false,
                AnimationState.MOVING).animationState());

        assertThrows(IllegalArgumentException.class, () -> kinematics(
                MovementState.DOCKED,
                ActorPhase.AVAILABLE,
                0,
                true,
                AnimationState.DOCKED));
    }

    @Test
    void rejectsInvalidTickAndActorPhaseRemainingTicks() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerKinematics(
                -1,
                0.0,
                0.0,
                Direction.UP,
                MovementState.CRUISING,
                ActorPhase.AVAILABLE,
                0,
                false,
                AnimationState.MOVING));
        assertThrows(IllegalArgumentException.class, () -> kinematics(
                MovementState.CRUISING,
                ActorPhase.AVAILABLE,
                1,
                false,
                AnimationState.MOVING));
        assertThrows(IllegalArgumentException.class, () -> kinematics(
                MovementState.CRUISING,
                ActorPhase.PHASED,
                -1,
                false,
                AnimationState.MOVING));
    }

    private static PlayerKinematics kinematics(
            MovementState movementState,
            ActorPhase actorPhase,
            int actorPhaseTicksRemaining,
            boolean interactionTriggered,
            AnimationState animationState) {
        return new PlayerKinematics(
                3,
                96.0,
                144.0,
                Direction.RIGHT,
                movementState,
                actorPhase,
                actorPhaseTicksRemaining,
                interactionTriggered,
                animationState);
    }
}
