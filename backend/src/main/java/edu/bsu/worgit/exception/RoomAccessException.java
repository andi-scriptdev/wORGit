package edu.bsu.worgit.exception;

/** 403. The app shows SYS-01 for organization and peer rooms; interest rooms use the inline locked panel. */
public class RoomAccessException extends InvalidInputException {
    public RoomAccessException(String message) {
        super(message);
    }

    @Override
    public int httpStatus() {
        return 403;
    }

    @Override
    public String code() {
        return "ROOM_ACCESS";
    }
}
