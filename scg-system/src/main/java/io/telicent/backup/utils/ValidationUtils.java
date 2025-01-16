package io.telicent.backup.utils;

import java.io.File;

public class ValidationUtils {

    public static void unpackBackup(final File directory){
        if (null == directory || !directory.exists()) {
            return;
        }
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                }
            }
        }
        directory.delete();
    }

    public static void convertToTurtle(){}

    public static void validate(){}

    public static void saveReport(){}

    public static void cleanUp(){}
}
