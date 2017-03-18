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
package org.hellojavaer.fatjar.core.boot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">Kaiming Zou</a>,created on 18/03/2017.
 */
class MainEntry {

    static {
        System.out.println("MainEntry is loaded by " + MainEntry.class.getClassLoader());
    }

    public static void invoke(String className, String[] args)
            throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, NoSuchMethodException,
                   InstantiationException {
        Class startClass = Class.forName(className);
        Method method = startClass.getMethod("main", String[].class);
        System.out.println(method.toString());
        method.invoke(null, (Object)args);
    }
}
