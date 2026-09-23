package com.aparcar.api.config;

public final class ApplicationConstants {
    public static final String JWT_SECRET_KEY = "JWT_SECRET";
    public static final String JWT_SECRET_DEFAULT = "IPjxwMLwblNfL3FPj8w64mp3fVDyqrgG";
    public static final String JWT_HEADER = "Authorization";
    public static final String PROD_ENV = "prod";
    public static final String DEV_ENV = "dev";
    public static final String NOT_DEV_ENV = "!" + DEV_ENV;
    public static final String TEST_ENV = "test";
    public static final String NOT_TEST_ENV = "!" + TEST_ENV;
}
