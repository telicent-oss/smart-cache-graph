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

package yamlconfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.ArrayList;
import java.util.List;

import static yamlconfig.ConfigConstants.log;

/** Log appender used in the tests to check if the log warnings are correct. */
public class TestLogAppender extends AbstractAppender {

    private final List<LogEvent> logEvents = new ArrayList<>();

    protected TestLogAppender(String name, Filter filter) {
        super(name, filter, PatternLayout.createDefaultLayout(), true);
    }

    @Override
    public void append(LogEvent event) {
        logEvents.add(event);
    }

    public List<LogEvent> getLogEvents() {
        return logEvents;
    }

    public static TestLogAppender createAndRegister() {
        TestLogAppender appender = new TestLogAppender("TestLogAppender", null);
        appender.start();
        Logger logger = LogManager.getLogger(log);
        ((org.apache.logging.log4j.core.Logger) logger).addAppender(appender);
        return appender;
    }
}