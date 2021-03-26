package no.nordicsemi.android.blinky.profile.data;

public enum e_window_state
{
    WINDOW_IDLE_STATE,
    WINDOW_ERROR_STATE,
    WINDOW_OPENED_STATE,
    WINDOW_CLOSED_STATE,
    WINDOW_MOVING_STATE;

    public static e_window_state fromInteger(int x) {
        switch(x) {
            case 0:
                return WINDOW_IDLE_STATE;
            case 1:
                return WINDOW_ERROR_STATE;
            case 2:
                return WINDOW_OPENED_STATE;
            case 3:
                return WINDOW_CLOSED_STATE;
            case 4:
                return WINDOW_MOVING_STATE;
        }
        return null;
    }
}
