package com.cobblemoncarrots.healer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealCalculatorTest {

    private fun call(
        hpDeficits: List<Int> = emptyList(),
        faintedMaxHps: List<Int> = emptyList(),
        hpPerCarrot: Int = 60,
        healerReviveCarrotCost: Int = 3,
        carrotsInInventory: Int = 0,
        carrotPrice: Int = 5,
    ) = HealCalculator.compute(
        hpDeficits, faintedMaxHps,
        hpPerCarrot, healerReviveCarrotCost, carrotsInInventory, carrotPrice,
    )

    @Test fun `no damage no fainted no carrots no cost`() {
        val r = call()
        assertEquals(0, r.totalCarrots); assertEquals(0, r.moneyCost)
    }

    @Test fun `1 hp deficit still needs 1 carrot`() {
        assertEquals(1, call(hpDeficits = listOf(1)).carrotsForHealing)
    }

    @Test fun `60 hp deficit equals exactly 1 carrot`() {
        assertEquals(1, call(hpDeficits = listOf(60)).carrotsForHealing)
    }

    @Test fun `61 hp deficit equals 2 carrots`() {
        assertEquals(2, call(hpDeficits = listOf(61)).carrotsForHealing)
    }

    @Test fun `pooled beats per-mon ceiling`() {
        // 3 mons × 20 HP each = manual would be 3 carrots; pooled = ceil(60 / 60) = 1.
        assertEquals(1, call(hpDeficits = listOf(20, 20, 20)).carrotsForHealing)
    }

    @Test fun `fainted mon at 60 maxHp costs only the revive`() {
        val r = call(faintedMaxHps = listOf(60))
        assertEquals(3, r.carrotsForRevives)
        assertEquals(0, r.carrotsForHealing)
        assertEquals(3, r.totalCarrots)
    }

    @Test fun `fainted mon with large maxHp adds pooled deficit beyond the free 60`() {
        // 1 fainted, maxHp=200 → revive 3 + (200-60)=140 deficit pooled = ceil(140/60)=3 heal.
        val r = call(faintedMaxHps = listOf(200))
        assertEquals(3, r.carrotsForRevives)
        assertEquals(3, r.carrotsForHealing)
        assertEquals(6, r.totalCarrots)
    }

    @Test fun `fainted post-revive deficit pools with living mons`() {
        // Living=20, fainted post-revive=140, pooled=160 → ceil(160/60)=3 heal. + 3 revive = 6.
        val r = call(hpDeficits = listOf(20), faintedMaxHps = listOf(200))
        assertEquals(3, r.carrotsForHealing)
        assertEquals(3, r.carrotsForRevives)
        assertEquals(6, r.totalCarrots)
    }

    @Test fun `inventory subtracts from short`() {
        val r = call(hpDeficits = listOf(180), carrotsInInventory = 1)
        assertEquals(3, r.carrotsForHealing); assertEquals(2, r.carrotsShort); assertEquals(10, r.moneyCost)
    }

    @Test fun `inventory fully covers means zero money`() {
        val r = call(hpDeficits = listOf(180), carrotsInInventory = 10)
        assertEquals(3, r.carrotsForHealing); assertEquals(0, r.carrotsShort); assertEquals(0, r.moneyCost)
    }

    @Test fun `tiny fainted mon under hpPerCarrot costs only the revive`() {
        // maxHp=40 — revive heals to maxHp (capped). (40-60).coerceAtLeast(0)=0 deficit.
        val r = call(faintedMaxHps = listOf(40))
        assertEquals(3, r.carrotsForRevives); assertEquals(0, r.carrotsForHealing)
    }
}
