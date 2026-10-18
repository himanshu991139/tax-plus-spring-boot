package com.meesho.gst.exception;

/**
 * Custom runtime exception thrown when Meesho Excel report parsing,
 * validation, or GSTR-1 conversion fails.
 */
public class GstConversionException extends RuntimeException {

    public GstConversionException(String message) {
        super(message);
    }

    public GstConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
