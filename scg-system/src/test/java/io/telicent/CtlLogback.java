/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.telicent;

import ch.qos.logback.classic.Level;

public class CtlLogback {

    private CtlLogback() {}

    public static void withLevel(org.slf4j.Logger logger, String execLevel, Runnable action) {
        String currentLevel = setLevel(logger, execLevel);
        try {
            action.run();
        } finally {
            setLevel(logger, currentLevel);
        }
    }

    private static Level name2level(String levelName) {
        Level level = Level.ALL;
        if ( levelName == null )
            level = null;
        else if ( levelName.equalsIgnoreCase("info") )
            level = Level.INFO;
        else if ( levelName.equalsIgnoreCase("debug") )
            level = Level.DEBUG;
        else if ( levelName.equalsIgnoreCase("warn") || levelName.equalsIgnoreCase("warning") )
            level = Level.WARN;
        else if ( levelName.equalsIgnoreCase("error") || levelName.equalsIgnoreCase("severe") )
            level = Level.ERROR;
        else if ( levelName.equalsIgnoreCase("trace") )
            level = Level.TRACE;
        else if ( levelName.equalsIgnoreCase("fatal") )
            // Logback-ism.
            level = Level.OFF;
        else if ( levelName.equalsIgnoreCase("OFF") )
            level = Level.OFF;
        return level;
    }

    private static String level2name(Level level) {
        if ( level == null )
            level = null;
        return level.levelStr;
    }

    static String getLevel(org.slf4j.Logger logger) {
        ch.qos.logback.classic.Logger logback = toLogback(logger);
        Level level = logback.getLevel();
        return level2name(level);
    }

    static String setLevel(org.slf4j.Logger logger, String levelName) {
        ch.qos.logback.classic.Logger logback = toLogback(logger);
        Level newLevel = name2level(levelName);
        // ** Not set by logback (1.3.14)
        //ch.qos.logback.classic.Level oldLevel = logback.getLevel();
        Level oldLevel = logback.getEffectiveLevel();
        logback.setLevel(newLevel);
        return oldLevel.levelStr;
    }

    private static ch.qos.logback.classic.Logger toLogback(org.slf4j.Logger logger) {
        return (ch.qos.logback.classic.Logger)logger;
    }
}
