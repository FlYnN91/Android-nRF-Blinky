package no.nordicsemi.android.blinky.profile.data;

public enum e_lock_state {
    LOCK_IDLE_STATE,
    LOCK_ERROR_STATE,
    LOCK_UNLOCKED_STATE,
    LOCK_LOCKED_STATE,
    LOCK_MOVING_STATE;

    public static e_lock_state fromInteger(int x) {
        switch(x) {
            case 0:
                return LOCK_IDLE_STATE;
            case 1:
                return LOCK_ERROR_STATE;
            case 2:
                return LOCK_UNLOCKED_STATE;
            case 3:
                return LOCK_LOCKED_STATE;
            case 4:
                return LOCK_MOVING_STATE;
        }
        return null;
    }
}
