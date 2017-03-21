/*
 * Copyright 2017-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hellojavaer.fatjar.core;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 18/03/2017.
 */
class Logger {

    public boolean isErrorEnabled() {
        if (FatJarSystemConfig.getLogLevel() >= 0) {
            return true;
        } else {
            return false;
        }
    }

    public void error(String msg) {
        System.out.println("ERROR: [FatJar] -| " + msg);
    }

    public boolean isWarnEnabled() {
        if (FatJarSystemConfig.getLogLevel() >= 1) {
            return true;
        } else {
            return false;
        }
    }

    public void warn(String msg) {
        System.out.println("WARN: [FatJar] —| " + msg);
    }

    public boolean isInfoEnabled() {
        if (FatJarSystemConfig.getLogLevel() >= 2) {
            return true;
        } else {
            return false;
        }
    }

    public void info(String msg) {
        System.out.println("INFO: [FatJar] -| " + msg);
    }

    public boolean isDebugEnabled() {
        if (FatJarSystemConfig.getLogLevel() >= 3) {
            return true;
        } else {
            return false;
        }
    }

    public void debug(String msg) {
        System.out.println("DEBUG: [FatJar] -| " + msg);
    }

}
