package com.kioskmdm;

public final class BackHoldStateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        BackHoldState hold = new BackHoldState();
        check(hold.down(100, false), "first down must arm");
        check(!hold.fireIfDue(5099), "4999ms must not trigger");
        check(hold.fireIfDue(5100), "5000ms must trigger");
        check(!hold.fireIfDue(6000), "held button must not trigger twice");
        hold.cancel();
        check(!hold.fireIfDue(9000), "release must cancel");
        for (int i = 0; i < 10; i++) {
            check(hold.down(i * 1000L, false), "short down");
            check(!hold.fireIfDue(i * 1000L + 499), "short press");
            hold.cancel();
        }
        check(!hold.fireIfDue(30000), "many short presses must not accumulate");
        check(!hold.down(30000, true), "orphan repeat must not arm");
        check(hold.down(31000, false), "arm after repeated presses");
        check(!hold.down(31500, true), "repeat must not reset timer");
        check(hold.fireIfDue(36000), "repeat must preserve original deadline");
        hold.cancel();
        check(hold.down(40000, false), "new hold after screen-off");
        hold.cancel();
        check(!hold.fireIfDue(45000), "screen-off/interruption must cancel pending hold");
        System.out.println("Back hold timing: all checks passed");
    }
}
